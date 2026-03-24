package com.supermap.udbx.dataset;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.geometry.gaia.GaiaGeometryReader;
import com.supermap.udbx.geometry.gaia.GaiaGeometryWriter;
import com.supermap.udbx.streaming.AutoCloseableStream;
import com.supermap.udbx.streaming.FeatureSpliterator;
import com.supermap.udbx.system.SmRegisterDao;
import org.locationtech.jts.geom.Point;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * 点数据集实现（DatasetType=Point，geoType=1）。
 *
 * <p>从数据表（如 BaseMap_P）读取点要素，几何数据存储于 SmGeometry 列（GAIA 格式 BLOB）。
 * 属性数据从数据表的用户字段列读取。
 *
 * <p>系统字段（SmID、SmUserID、SmGeometry）已过滤，不出现在 {@link PointFeature#attributes()} 中。
 */
public class PointDataset extends VectorDataset {

    /** 数据表中的几何列名（GAIA 格式 BLOB）。 */
    private static final String GEOMETRY_COLUMN = "SmGeometry";

    /** 数据表中的系统字段前缀（以此开头的列不计入用户属性）。 */
    private static final String SYSTEM_COLUMN_PREFIX = "Sm";

    public PointDataset(Connection conn, DatasetInfo info) {
        super(conn, info);
    }

    /**
     * 读取该数据集中的所有点要素。
     *
     * @return 要素列表（按 SmID 升序）
     * @throws RuntimeException 若数据库查询失败
     */
    public List<PointFeature> getFeatures() {
        if (!tableExists()) return List.of();
        String sql = "SELECT * FROM \"" + getTableName() + "\" ORDER BY SmID";

        // 预分配 ArrayList 容量以减少扩容开销
        int estimatedCount = info.objectCount();
        int initialCapacity = Math.max(16, Math.min(estimatedCount, 1_000_000));
        List<PointFeature> features = new ArrayList<>(initialCapacity);

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<String> userColumns = resolveUserColumns(rs.getMetaData());
            while (rs.next()) {
                features.add(mapRow(rs, userColumns));
            }
        } catch (SQLException e) {
            throw new RuntimeException("读取点数据集 [" + getName() + "] 失败", e);
        }
        return features;
    }

    /**
     * 根据 SmID 读取单个点要素。
     *
     * @param smId 要素 SmID
     * @return 要素对象，不存在时返回 {@code null}
     * @throws RuntimeException 若数据库查询失败
     */
    public PointFeature getFeature(int smId) {
        String sql = "SELECT * FROM \"" + getTableName() + "\" WHERE SmID = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, smId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                List<String> userColumns = resolveUserColumns(rs.getMetaData());
                return mapRow(rs, userColumns);
            }
        } catch (SQLException e) {
            throw new RuntimeException("读取点要素 SmID=" + smId + " 失败", e);
        }
    }

    /**
     * 分页查询点要素。
     *
     * @param offset 起始位置（从 0 开始）
     * @param limit  每页数量
     * @return 要素列表（按 SmID 升序）
     * @throws RuntimeException 若数据库查询失败
     */
    public List<PointFeature> getFeatures(int offset, int limit) {
        String sql = "SELECT * FROM \"" + getTableName() + "\" ORDER BY SmID LIMIT ? OFFSET ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                List<PointFeature> features = new ArrayList<>(limit);
                List<String> userColumns = resolveUserColumns(rs.getMetaData());
                while (rs.next()) {
                    features.add(mapRow(rs, userColumns));
                }
                return features;
            }
        } catch (SQLException e) {
            throw new RuntimeException("分页查询失败", e);
        }
    }

    /**
     * 查询数据集中的要素总数。
     *
     * @return 要素数量
     * @throws RuntimeException 若数据库查询失败
     */
    public int getCount() {
        String sql = "SELECT COUNT(*) FROM \"" + getTableName() + "\"";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("查询要素数量失败", e);
        }
    }

    /**
     * 流式读取点数据集的所有要素。
     *
     * <p>返回一个可自动关闭的 Stream，避免一次性加载所有数据到内存。
     * 使用 try-with-resources 确保资源释放：
     * <pre>{@code
     * try (var stream = pointDataset.streamFeatures()) {
     *     stream.getStream().forEach(feature -> process(feature));
     * }
     * }</pre>
     *
     * @return 包装了 Stream 和资源的 AutoCloseableStream
     * @throws RuntimeException 若创建流失败
     */
    @Override
    public AutoCloseableStream<PointFeature> streamFeatures() {
        if (!tableExists()) {
            // 表不存在时返回空 Stream
            return new AutoCloseableStream<>(java.util.stream.Stream.empty(), () -> {});
        }

        FeatureSpliterator<PointFeature> spliterator;
        try {
            spliterator = new FeatureSpliterator<>(
                conn,
                info,
                getTableName(),
                this::mapRowForStream
            );
        } catch (SQLException e) {
            throw new RuntimeException("创建 FeatureSpliterator 失败: " + e.getMessage(), e);
        }

        try {
            return new AutoCloseableStream<>(
                StreamSupport.stream(spliterator, false),
                spliterator
            );
        } catch (Exception e) {
            // 创建 Stream 失败时，确保关闭 spliterator
            spliterator.close();
            throw new RuntimeException("创建流式读取失败: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // 写入方法
    // -----------------------------------------------------------------------

    /**
     * 批量写入点要素（高性能）。
     *
     * <p>使用 JDBC Batch API 在单个事务中写入多个要素，显著提升批量写入性能。
     * 自动更新 SmRegister 中的 SmObjectCount 和 SmMaxGeometrySize（仅一次）。
     *
     * @param features 点要素列表
     * @throws RuntimeException 若批量写入失败
     */
    public void addFeaturesBatch(List<PointFeature> features) {
        if (features.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO \"" + getTableName() +
                "\" (SmID, SmUserID, \"SmGeometry\") VALUES (?, 0, ?)";

        try {
            conn.setAutoCommit(false);

            int maxGeomSize = 0;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (PointFeature f : features) {
                    ps.setInt(1, f.smId());
                    byte[] geomBytes = GaiaGeometryWriter.writePoint(f.geometry(), info.srid());
                    ps.setBytes(2, geomBytes);
                    ps.addBatch();

                    // 跟踪最大几何尺寸
                    if (geomBytes.length > maxGeomSize) {
                        maxGeomSize = geomBytes.length;
                    }
                }

                ps.executeBatch();
            }

            // 更新 SmRegister（仅一次）
            new SmRegisterDao(conn).incrementObjectCountBatch(
                info.datasetId(), features.size(), maxGeomSize);

            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("批量写入失败", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * 向数据集中写入一个点要素。
     *
     * <p>自动更新 SmRegister 中的 SmObjectCount 和 SmMaxGeometrySize。
     * 每次调用都包含在一个独立事务中。
     *
     * @param smId       要素 SmID（必须唯一）
     * @param geometry   JTS Point（2D）
     * @param attributes 用户属性字段（暂不写入，预留接口）
     * @throws RuntimeException 若写入失败
     */
    public void addFeature(int smId, Point geometry, Map<String, Object> attributes) {
        byte[] geomBytes = GaiaGeometryWriter.writePoint(geometry, info.srid());
        List<String> attrKeys = attributes == null ? java.util.List.of() : new ArrayList<>(attributes.keySet());

        StringBuilder sqlBuf = new StringBuilder("INSERT INTO \"").append(getTableName())
                .append("\" (SmID, SmUserID, \"SmGeometry\"");
        for (String key : attrKeys) {
            sqlBuf.append(", \"").append(key).append("\"");
        }
        sqlBuf.append(") VALUES (?, 0, ?");
        for (int i = 0; i < attrKeys.size(); i++) {
            sqlBuf.append(", ?");
        }
        sqlBuf.append(")");

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sqlBuf.toString())) {
                stmt.setInt(1, smId);
                stmt.setBytes(2, geomBytes);
                for (int i = 0; i < attrKeys.size(); i++) {
                    stmt.setObject(3 + i, attributes.get(attrKeys.get(i)));
                }
                stmt.executeUpdate();
            }
            new SmRegisterDao(conn).incrementObjectCount(info.datasetId(), geomBytes.length);
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("写入点要素 SmID=" + smId + " 失败", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * 删除指定 SmID 的点要素。
     *
     * @param smId 要素 SmID
     * @throws RuntimeException 若要素不存在或删除失败
     */
    public void deleteFeature(int smId) {
        String tableName = info.datasetName();
        String sql = "DELETE FROM \"" + tableName + "\" WHERE SmID = ?";

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, smId);
                int affected = stmt.executeUpdate();
                if (affected == 0) {
                    throw new SQLException("SmID=" + smId + " 不存在");
                }
            }
            new SmRegisterDao(conn).decrementObjectCount(info.datasetId());
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("删除点要素 SmID=" + smId + " 失败: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * 更新指定 SmID 的点要素。
     *
     * @param smId       要素 SmID
     * @param geometry   新几何（可为 null 表示不更新几何）
     * @param attributes 新属性（可为 null 表示不更新属性）
     * @throws RuntimeException 若要素不存在或更新失败
     */
    public void updateFeature(int smId, Point geometry, Map<String, Object> attributes) {
        String tableName = info.datasetName();
        List<String> attrKeys = attributes == null ? List.of() : new ArrayList<>(attributes.keySet());

        // 构建动态 UPDATE SQL
        StringBuilder sqlBuf = new StringBuilder("UPDATE \"").append(tableName).append("\" SET ");
        boolean hasSet = false;

        if (geometry != null) {
            sqlBuf.append("\"SmGeometry\" = ?");
            hasSet = true;
        }

        for (String key : attrKeys) {
            if (hasSet) sqlBuf.append(", ");
            sqlBuf.append("\"").append(key).append("\" = ?");
            hasSet = true;
        }

        if (!hasSet) {
            return; // 无更新内容
        }

        sqlBuf.append(" WHERE SmID = ?");

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sqlBuf.toString())) {
                int paramIdx = 1;

                if (geometry != null) {
                    byte[] geomBytes = GaiaGeometryWriter.writePoint(geometry, info.srid());
                    stmt.setBytes(paramIdx++, geomBytes);
                }

                for (String key : attrKeys) {
                    stmt.setObject(paramIdx++, attributes.get(key));
                }

                stmt.setInt(paramIdx, smId);
                int affected = stmt.executeUpdate();
                if (affected == 0) {
                    throw new SQLException("SmID=" + smId + " 不存在");
                }
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("更新点要素 SmID=" + smId + " 失败: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // -----------------------------------------------------------------------
    // 私有辅助方法
    // -----------------------------------------------------------------------

    /**
     * 将当前行映射为 PointFeature（用于流式读取）。
     *
     * <p>每次读取时动态解析用户字段，避免在 Spliterator 构造时执行元数据查询。
     *
     * @param rs ResultSet（已定位到目标行）
     * @return PointFeature 对象
     * @throws SQLException 若读取失败
     */
    private PointFeature mapRowForStream(ResultSet rs) throws SQLException {
        int smId = rs.getInt("SmID");
        byte[] geomBytes = rs.getBytes(GEOMETRY_COLUMN);
        Point geometry = GaiaGeometryReader.readPoint(geomBytes);

        // 动态解析用户字段
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> attributes = new HashMap<>();
        int columnCount = meta.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String columnName = meta.getColumnName(i);
            if (!columnName.startsWith(SYSTEM_COLUMN_PREFIX)) {
                attributes.put(columnName, rs.getObject(i));
            }
        }

        return new PointFeature(smId, geometry, attributes);
    }

    /**
     * 从 ResultSetMetaData 中提取用户字段列名（排除系统字段）。
     *
     * <p>系统字段规则：列名以 "Sm" 开头（大小写敏感）。
     */
    private List<String> resolveUserColumns(ResultSetMetaData meta) throws SQLException {
        List<String> columns = new ArrayList<>();
        int count = meta.getColumnCount();
        for (int i = 1; i <= count; i++) {
            String name = meta.getColumnName(i);
            if (!name.startsWith(SYSTEM_COLUMN_PREFIX)) {
                columns.add(name);
            }
        }
        return columns;
    }

    /**
     * 将当前行映射为 PointFeature。
     *
     * @param rs          ResultSet（已定位到目标行）
     * @param userColumns 用户字段列名列表
     */
    private PointFeature mapRow(ResultSet rs, List<String> userColumns) throws SQLException {
        int smId = rs.getInt("SmID");
        byte[] geomBytes = rs.getBytes(GEOMETRY_COLUMN);
        Point geometry = GaiaGeometryReader.readPoint(geomBytes);

        Map<String, Object> attributes = new HashMap<>();
        for (String col : userColumns) {
            attributes.put(col, rs.getObject(col));
        }

        return new PointFeature(smId, geometry, attributes);
    }
}
