package com.supermap.udbx.dataset;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.core.QueryOptions;
import com.supermap.udbx.geometry.gaia.GaiaGeometryReader;
import com.supermap.udbx.geometry.gaia.GaiaGeometryWriter;
import com.supermap.udbx.streaming.AutoCloseableStream;
import com.supermap.udbx.streaming.FeatureSpliterator;
import com.supermap.udbx.system.SmRegisterDao;
import org.locationtech.jts.geom.MultiLineString;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * 三维线数据集实现（DatasetKind=LineZ=103，geoType=1005 GAIAMultiLineStringZ）。
 *
 * <p>对应白皮书 §3.1.3（三维线数据集）和 §4.2.4（GAIAMultiLineStringZ 格式）。
 */
public class LineZDataset extends VectorDataset {

    private static final String GEOMETRY_COLUMN = "SmGeometry";
    private static final String SYSTEM_COLUMN_PREFIX = "Sm";

    public LineZDataset(Connection conn, DatasetInfo info) {
        super(conn, info);
    }

    /**
     * 读取该数据集中的所有三维线要素。
     */
    public List<LineFeature> list() {
        return list(QueryOptions.EMPTY);
    }

    /**
     * 按查询选项读取三维线要素。
     */
    public List<LineFeature> list(QueryOptions options) {
        if (!tableExists()) return List.of();
        var sqlBuilder = new StringBuilder("SELECT * FROM \"").append(getTableName()).append("\"");
        var params = new ArrayList<Object>();

        if (options != null && options.ids() != null && !options.ids().isEmpty()) {
            var placeholders = String.join(", ", Collections.nCopies(options.ids().size(), "?"));
            sqlBuilder.append(" WHERE SmID IN (").append(placeholders).append(")");
            params.addAll(options.ids());
        }
        sqlBuilder.append(" ORDER BY SmID");

        if (options != null && options.limit() != null) {
            sqlBuilder.append(" LIMIT ?");
            params.add(options.limit());
        }
        if (options != null && options.offset() != null) {
            sqlBuilder.append(" OFFSET ?");
            params.add(options.offset());
        }

        int estimatedCount = info.objectCount();
        int initialCapacity = Math.max(16, Math.min(estimatedCount, 1_000_000));
        List<LineFeature> features = new ArrayList<>(initialCapacity);

        try (PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<String> userColumns = resolveUserColumns(rs.getMetaData());
                while (rs.next()) {
                    features.add(mapRow(rs, userColumns));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("读取三维线数据集 [" + getName() + "] 失败", e);
        }
        return features;
    }

    /**
     * 根据 id 读取单个三维线要素。
     */
    public LineFeature getById(int id) {
        String sql = "SELECT * FROM \"" + getTableName() + "\" WHERE SmID = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                List<String> userColumns = resolveUserColumns(rs.getMetaData());
                return mapRow(rs, userColumns);
            }
        } catch (SQLException e) {
            throw new RuntimeException("读取三维线要素 id=" + id + " 失败", e);
        }
    }

    /**
     * 查询数据集中的要素总数。
     */
    public int count() {
        String sql = "SELECT COUNT(*) FROM \"" + getTableName() + "\"";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("查询要素数量失败", e);
        }
    }

    /**
     * 流式读取三维线数据集的所有要素。
     */
    @Override
    public AutoCloseableStream<LineFeature> stream() {
        return stream(QueryOptions.EMPTY);
    }

    /**
     * 按查询选项流式读取三维线要素。
     */
    @Override
    public AutoCloseableStream<LineFeature> stream(QueryOptions options) {
        if (!tableExists()) {
            return new AutoCloseableStream<>(java.util.stream.Stream.empty(), () -> {});
        }

        FeatureSpliterator<LineFeature> spliterator;
        try {
            spliterator = new FeatureSpliterator<>(
                conn, info, getTableName(), this::mapRowForStream, options);
        } catch (SQLException e) {
            throw new RuntimeException("创建 FeatureSpliterator 失败: " + e.getMessage(), e);
        }

        try {
            return new AutoCloseableStream<>(
                StreamSupport.stream(spliterator, false), spliterator);
        } catch (Exception e) {
            spliterator.close();
            throw new RuntimeException("创建流式读取失败: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // 写入方法
    // -----------------------------------------------------------------------

    /**
     * 批量写入三维线要素（高性能）。
     */
    public void insertMany(List<LineFeature> features) {
        if (features == null || features.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO \"" + getTableName() +
                "\" (SmID, SmUserID, \"SmGeometry\") VALUES (?, 0, ?)";

        try {
            conn.setAutoCommit(false);

            int maxGeomSize = 0;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (LineFeature f : features) {
                    ps.setInt(1, f.id());
                    byte[] geomBytes = GaiaGeometryWriter.writeMultiLineStringZ(f.geometry(), info.srid());
                    ps.setBytes(2, geomBytes);
                    ps.addBatch();

                    if (geomBytes.length > maxGeomSize) {
                        maxGeomSize = geomBytes.length;
                    }
                }

                ps.executeBatch();
            }

            new SmRegisterDao(conn).incrementObjectCountBatch(
                info.id(), features.size(), maxGeomSize);

            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("批量写入失败", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * 向数据集中写入一个三维线要素。
     */
    public void insert(LineFeature feature) {
        byte[] geomBytes = GaiaGeometryWriter.writeMultiLineStringZ(feature.geometry(), info.srid());
        Map<String, Object> attributes = feature.attributes();
        List<String> attrKeys = attributes == null || attributes.isEmpty()
                ? java.util.List.of()
                : new ArrayList<>(attributes.keySet());

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
                stmt.setInt(1, feature.id());
                stmt.setBytes(2, geomBytes);
                for (int i = 0; i < attrKeys.size(); i++) {
                    stmt.setObject(3 + i, attributes.get(attrKeys.get(i)));
                }
                stmt.executeUpdate();
            }
            new SmRegisterDao(conn).incrementObjectCount(info.id(), geomBytes.length);
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("写入三维线要素 id=" + feature.id() + " 失败", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * 删除指定 id 的三维线要素。
     */
    public void delete(int id) {
        String sql = "DELETE FROM \"" + getTableName() + "\" WHERE SmID = ?";

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                int affected = stmt.executeUpdate();
                if (affected == 0) {
                    throw new SQLException("id=" + id + " 不存在");
                }
            }
            new SmRegisterDao(conn).decrementObjectCount(info.id());
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("删除三维线要素 id=" + id + " 失败: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * 更新指定 id 的三维线要素。
     */
    public void update(int id, MultiLineString geometry, Map<String, Object> attributes) {
        List<String> attrKeys = attributes == null ? List.of() : new ArrayList<>(attributes.keySet());

        StringBuilder sqlBuf = new StringBuilder("UPDATE \"").append(getTableName()).append("\" SET ");
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

        if (!hasSet) return;

        sqlBuf.append(" WHERE SmID = ?");

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sqlBuf.toString())) {
                int paramIdx = 1;

                if (geometry != null) {
                    stmt.setBytes(paramIdx++, GaiaGeometryWriter.writeMultiLineStringZ(geometry, info.srid()));
                }

                for (String key : attrKeys) {
                    stmt.setObject(paramIdx++, attributes.get(key));
                }

                stmt.setInt(paramIdx, id);
                int affected = stmt.executeUpdate();
                if (affected == 0) {
                    throw new SQLException("id=" + id + " 不存在");
                }
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("更新三维线要素 id=" + id + " 失败: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // -----------------------------------------------------------------------
    // 私有辅助方法
    // -----------------------------------------------------------------------

    private LineFeature mapRowForStream(ResultSet rs) throws SQLException {
        int id = rs.getInt("SmID");
        byte[] geomBytes = rs.getBytes(GEOMETRY_COLUMN);
        MultiLineString geometry = GaiaGeometryReader.readMultiLineStringZ(geomBytes);

        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> attributes = new HashMap<>();
        int columnCount = meta.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String columnName = meta.getColumnName(i);
            if (!columnName.startsWith(SYSTEM_COLUMN_PREFIX)) {
                attributes.put(columnName, rs.getObject(i));
            }
        }

        return new LineFeature(id, geometry, attributes);
    }

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

    private LineFeature mapRow(ResultSet rs, List<String> userColumns) throws SQLException {
        int id = rs.getInt("SmID");
        byte[] geomBytes = rs.getBytes(GEOMETRY_COLUMN);
        MultiLineString geometry = GaiaGeometryReader.readMultiLineStringZ(geomBytes);

        Map<String, Object> attributes = new HashMap<>();
        for (String col : userColumns) {
            attributes.put(col, rs.getObject(col));
        }

        return new LineFeature(id, geometry, attributes);
    }
}
