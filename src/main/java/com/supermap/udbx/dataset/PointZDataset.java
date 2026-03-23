package com.supermap.udbx.dataset;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.geometry.gaia.GaiaGeometryReader;
import com.supermap.udbx.geometry.gaia.GaiaGeometryWriter;
import com.supermap.udbx.system.SmRegisterDao;
import org.locationtech.jts.geom.Point;

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
 * 三维点数据集实现（DatasetType=PointZ=101，geoType=1001 GAIAPointZ）。
 *
 * <p>几何数据存储于 SmGeometry 列（GAIA PointZ 格式 BLOB），Z 坐标通过 JTS Point 的 Z 值存取。
 *
 * <p>对应白皮书 §3.1.2（三维点数据集）和 §4.2.2（GAIAPointZ 格式）。
 */
public class PointZDataset extends VectorDataset {

    private static final String GEOMETRY_COLUMN = "SmGeometry";
    private static final String SYSTEM_COLUMN_PREFIX = "Sm";

    public PointZDataset(Connection conn, DatasetInfo info) {
        super(conn, info);
    }

    /**
     * 读取该数据集中的所有三维点要素。
     *
     * @return 要素列表（按 SmID 升序）
     * @throws RuntimeException 若数据库查询失败
     */
    public List<PointFeature> getFeatures() {
        if (!tableExists()) return List.of();
        String sql = "SELECT * FROM \"" + getTableName() + "\" ORDER BY SmID";
        List<PointFeature> features = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<String> userColumns = resolveUserColumns(rs.getMetaData());
            while (rs.next()) {
                features.add(mapRow(rs, userColumns));
            }
        } catch (SQLException e) {
            throw new RuntimeException("读取三维点数据集 [" + getName() + "] 失败", e);
        }
        return features;
    }

    /**
     * 根据 SmID 读取单个三维点要素。
     *
     * @param smId 要素 SmID
     * @return 要素对象，不存在时返回 {@code null}
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
            throw new RuntimeException("读取三维点要素 SmID=" + smId + " 失败", e);
        }
    }

    /**
     * 向数据集中写入一个三维点要素（JTS Point 的 Z 坐标会被写入）。
     *
     * @param smId       要素 SmID（必须唯一）
     * @param geometry   JTS Point（含 Z 坐标）
     * @param attributes 用户属性字段
     * @throws RuntimeException 若写入失败
     */
    public void addFeature(int smId, Point geometry, Map<String, Object> attributes) {
        byte[] geomBytes = GaiaGeometryWriter.writePointZ(geometry, info.srid());
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
            throw new RuntimeException("写入三维点要素 SmID=" + smId + " 失败", e);
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
            throw new RuntimeException("删除三维点要素 SmID=" + smId + " 失败: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void updateFeature(int smId, Point geometry, Map<String, Object> attributes) {
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
                if (geometry != null) stmt.setBytes(idx++, GaiaGeometryWriter.writePointZ(geometry, info.srid()));
                for (String key : attrKeys) stmt.setObject(idx++, attributes.get(key));
                stmt.setInt(idx, smId);
                if (stmt.executeUpdate() == 0) throw new SQLException("SmID=" + smId + " 不存在");
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("更新三维点要素 SmID=" + smId + " 失败: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
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

    private PointFeature mapRow(ResultSet rs, List<String> userColumns) throws SQLException {
        int smId = rs.getInt("SmID");
        byte[] geomBytes = rs.getBytes(GEOMETRY_COLUMN);
        Point geometry = GaiaGeometryReader.readPointZ(geomBytes);

        Map<String, Object> attributes = new HashMap<>();
        for (String col : userColumns) {
            attributes.put(col, rs.getObject(col));
        }

        return new PointFeature(smId, geometry, attributes);
    }
}
