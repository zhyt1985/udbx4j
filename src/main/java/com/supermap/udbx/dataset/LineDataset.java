package com.supermap.udbx.dataset;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.geometry.gaia.GaiaGeometryReader;
import com.supermap.udbx.geometry.gaia.GaiaGeometryWriter;
import com.supermap.udbx.system.SmRegisterDao;
import org.locationtech.jts.geom.MultiLineString;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 线数据集实现（DatasetType=Line，geoType=5 MultiLineString）。
 *
 * <p>从数据表（如 BaseMap_L）读取线要素，几何数据存储于 SmGeometry 列（GAIA MultiLineString 格式 BLOB）。
 * 属性数据从数据表的用户字段列读取。
 *
 * <p>对应白皮书 §3.1.3（线数据集）和 §4.2.3（GAIA MultiLineString 格式）。
 *
 * <p>系统字段（SmID、SmUserID、SmGeometry 等以 "Sm" 开头的列）不出现在
 * {@link LineFeature#attributes()} 中。
 */
public class LineDataset extends VectorDataset {

    /** 数据表中的几何列名（GAIA 格式 BLOB）。 */
    private static final String GEOMETRY_COLUMN = "SmGeometry";

    /** 系统字段前缀：以此开头的列不计入用户属性。 */
    private static final String SYSTEM_COLUMN_PREFIX = "Sm";

    public LineDataset(Connection conn, DatasetInfo info) {
        super(conn, info);
    }

    /**
     * 读取该数据集中的所有线要素。
     *
     * @return 要素列表（按 SmID 升序）
     * @throws RuntimeException 若数据库查询失败
     */
    public List<LineFeature> getFeatures() {
        if (!tableExists()) return List.of();
        String sql = "SELECT * FROM \"" + getTableName() + "\" ORDER BY SmID";

        // 预分配 ArrayList 容量以减少扩容开销
        int estimatedCount = info.objectCount();
        int initialCapacity = Math.max(16, Math.min(estimatedCount, 1_000_000));
        List<LineFeature> features = new ArrayList<>(initialCapacity);

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<String> userColumns = resolveUserColumns(rs.getMetaData());
            while (rs.next()) {
                features.add(mapRow(rs, userColumns));
            }
        } catch (SQLException e) {
            throw new RuntimeException("读取线数据集 [" + getName() + "] 失败", e);
        }
        return features;
    }

    /**
     * 根据 SmID 读取单个线要素。
     *
     * @param smId 要素 SmID
     * @return 要素对象，不存在时返回 {@code null}
     * @throws RuntimeException 若数据库查询失败
     */
    public LineFeature getFeature(int smId) {
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
            throw new RuntimeException("读取线要素 SmID=" + smId + " 失败", e);
        }
    }

    // -----------------------------------------------------------------------
    // 写入方法
    // -----------------------------------------------------------------------

    /**
     * 向数据集中写入一个线要素。
     */
    public void addFeature(int smId, MultiLineString geometry, Map<String, Object> attributes) {
        byte[] geomBytes = GaiaGeometryWriter.writeMultiLineString(geometry, info.srid());
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
            throw new RuntimeException("写入线要素 SmID=" + smId + " 失败", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void deleteFeature(int smId) {
        String sql = "DELETE FROM \"" + getTableName() + "\" WHERE SmID = ?";
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, smId);
                if (stmt.executeUpdate() == 0) throw new SQLException("SmID=" + smId + " 不存在");
            }
            new SmRegisterDao(conn).decrementObjectCount(info.datasetId());
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("删除线要素 SmID=" + smId + " 失败: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void updateFeature(int smId, MultiLineString geometry, Map<String, Object> attributes) {
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
                int idx = 1;
                if (geometry != null) stmt.setBytes(idx++, GaiaGeometryWriter.writeMultiLineString(geometry, info.srid()));
                for (String key : attrKeys) stmt.setObject(idx++, attributes.get(key));
                stmt.setInt(idx, smId);
                if (stmt.executeUpdate() == 0) throw new SQLException("SmID=" + smId + " 不存在");
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("更新线要素 SmID=" + smId + " 失败: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // -----------------------------------------------------------------------
    // 私有辅助方法
    // -----------------------------------------------------------------------

    /**
     * 从 ResultSetMetaData 中提取用户字段列名（排除以 "Sm" 开头的系统字段）。
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
     * 将当前行映射为 LineFeature。
     *
     * @param rs          ResultSet（已定位到目标行）
     * @param userColumns 用户字段列名列表
     */
    private LineFeature mapRow(ResultSet rs, List<String> userColumns) throws SQLException {
        int smId = rs.getInt("SmID");
        byte[] geomBytes = rs.getBytes(GEOMETRY_COLUMN);
        MultiLineString geometry = GaiaGeometryReader.readMultiLineString(geomBytes);

        Map<String, Object> attributes = new HashMap<>();
        for (String col : userColumns) {
            attributes.put(col, rs.getObject(col));
        }

        return new LineFeature(smId, geometry, attributes);
    }
}
