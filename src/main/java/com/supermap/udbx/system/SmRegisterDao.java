package com.supermap.udbx.system;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.core.DatasetType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SmRegister 系统表的数据访问对象。
 *
 * <p>对应白皮书 §2.1 表 5 SmRegister 字段定义。
 * 提供对矢量数据集元信息的 CRUD 操作。
 */
public class SmRegisterDao {

    private final Connection conn;

    public SmRegisterDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * 查询所有数据集元信息。
     *
     * @return 数据集元信息列表
     * @throws SQLException 查询失败时抛出
     */
    public List<DatasetInfo> findAll() throws SQLException {
        String sql = "SELECT SmDatasetID, SmDatasetName, SmDatasetType, SmObjectCount, SmSRID FROM SmRegister ORDER BY SmDatasetID";
        List<DatasetInfo> results = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    /**
     * 根据数据集 ID 查询元信息。
     *
     * @param datasetId 数据集 ID
     * @return 数据集元信息（如果存在）
     * @throws SQLException 查询失败时抛出
     */
    public Optional<DatasetInfo> findById(int datasetId) throws SQLException {
        String sql = "SELECT SmDatasetID, SmDatasetName, SmDatasetType, SmObjectCount, SmSRID FROM SmRegister WHERE SmDatasetID = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 根据数据集名称查询元信息。
     *
     * @param datasetName 数据集名称
     * @return 数据集元信息（如果存在）
     * @throws SQLException 查询失败时抛出
     */
    public Optional<DatasetInfo> findByName(String datasetName) throws SQLException {
        String sql = "SELECT SmDatasetID, SmDatasetName, SmDatasetType, SmObjectCount, SmSRID FROM SmRegister WHERE SmDatasetName = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, datasetName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    private DatasetInfo mapRow(ResultSet rs) throws SQLException {
        int datasetId = rs.getInt("SmDatasetID");
        String datasetName = rs.getString("SmDatasetName");
        int datasetTypeValue = rs.getInt("SmDatasetType");
        int objectCount = rs.getInt("SmObjectCount");
        int srid = rs.getInt("SmSRID");

        DatasetType datasetType = DatasetType.fromValue(datasetTypeValue);

        return new DatasetInfo(datasetId, datasetName, datasetType, objectCount, srid);
    }

    /**
     * 在 SmRegister 中插入新数据集记录（SmDatasetID 自动取最大 ID + 1）。
     *
     * @param datasetName  数据集名称
     * @param datasetType  数据集类型
     * @param srid         坐标系 ID
     * @param idColName    ID 列名（通常为 "SmID"）
     * @param geoColName   几何列名（通常为 "SmGeometry"，Tabular 为 null）
     * @return 分配的 SmDatasetID
     * @throws SQLException 操作失败时抛出
     */
    public int insert(String datasetName, DatasetType datasetType, int srid,
                      String idColName, String geoColName) throws SQLException {
        int newId = nextDatasetId();
        String sql = "INSERT INTO SmRegister " +
                "(SmDatasetID, SmDatasetName, SmTableName, SmDatasetType, SmObjectCount, " +
                " SmSRID, SmIDColName, SmGeoColName, SmMaxGeometrySize, SmCreateTime, SmLastUpdateTime) " +
                "VALUES (?, ?, ?, ?, 0, ?, ?, ?, 0, datetime('now'), datetime('now'))";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, newId);
            stmt.setString(2, datasetName);
            stmt.setString(3, datasetName);   // SmTableName = 数据集名称
            stmt.setInt(4, datasetType.getValue());
            stmt.setInt(5, srid);
            stmt.setString(6, idColName);
            if (geoColName != null) {
                stmt.setString(7, geoColName);
            } else {
                stmt.setNull(7, java.sql.Types.VARCHAR);
            }
            stmt.executeUpdate();
        }
        return newId;
    }

    /**
     * 更新 SmRegister 中数据集的要素计数与最大几何尺寸。
     *
     * @param datasetId       数据集 ID
     * @param newBlobSize     新插入的 Geometry BLOB 字节数
     * @throws SQLException 操作失败时抛出
     */
    public void incrementObjectCount(int datasetId, int newBlobSize) throws SQLException {
        String sql = "UPDATE SmRegister " +
                "SET SmObjectCount = SmObjectCount + 1, " +
                "    SmMaxGeometrySize = CASE WHEN SmMaxGeometrySize < ? THEN ? ELSE SmMaxGeometrySize END " +
                "WHERE SmDatasetID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, newBlobSize);
            stmt.setInt(2, newBlobSize);
            stmt.setInt(3, datasetId);
            stmt.executeUpdate();
        }
    }

    /**
     * 仅递增 SmObjectCount（用于无几何数据集）。
     */
    public void incrementObjectCount(int datasetId) throws SQLException {
        String sql = "UPDATE SmRegister SET SmObjectCount = SmObjectCount + 1 WHERE SmDatasetID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
            stmt.executeUpdate();
        }
    }

    /**
     * 递减 SmObjectCount（用于删除要素）。
     *
     * @param datasetId 数据集 ID
     * @throws SQLException 操作失败时抛出
     */
    public void decrementObjectCount(int datasetId) throws SQLException {
        String sql = "UPDATE SmRegister SET SmObjectCount = SmObjectCount - 1 WHERE SmDatasetID = ? AND SmObjectCount > 0";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
            stmt.executeUpdate();
        }
    }

    /**
     * 批量递增 SmObjectCount 并更新最大几何尺寸。
     *
     * @param datasetId    数据集 ID
     * @param count        递增数量
     * @param maxBlobSize  最大几何 BLOB 字节数
     * @throws SQLException 操作失败时抛出
     */
    public void incrementObjectCountBatch(int datasetId, int count, int maxBlobSize) throws SQLException {
        String sql = "UPDATE SmRegister " +
                "SET SmObjectCount = SmObjectCount + ?, " +
                "    SmMaxGeometrySize = CASE WHEN SmMaxGeometrySize < ? THEN ? ELSE SmMaxGeometrySize END " +
                "WHERE SmDatasetID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, count);
            stmt.setInt(2, maxBlobSize);
            stmt.setInt(3, maxBlobSize);
            stmt.setInt(4, datasetId);
            stmt.executeUpdate();
        }
    }

    private int nextDatasetId() throws SQLException {
        String sql = "SELECT COALESCE(MAX(SmDatasetID), 0) + 1 FROM SmRegister";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 1;
        }
    }
}
