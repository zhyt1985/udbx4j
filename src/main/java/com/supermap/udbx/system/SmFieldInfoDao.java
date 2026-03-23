package com.supermap.udbx.system;

import com.supermap.udbx.core.FieldInfo;
import com.supermap.udbx.core.FieldType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SmFieldInfo 系统表的数据访问对象。
 *
 * <p>对应白皮书 §2.2 表 10 SmFieldInfo 字段定义。
 * 提供对字段元信息的查询操作。
 */
public class SmFieldInfoDao {

    private final Connection conn;

    public SmFieldInfoDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * 根据数据集 ID 查询所有字段元信息。
     *
     * @param datasetId 数据集 ID
     * @return 字段元信息列表
     * @throws SQLException 查询失败时抛出
     */
    public List<FieldInfo> findByDatasetId(int datasetId) throws SQLException {
        String sql = "SELECT SmDatasetID, SmFieldName, SmFieldType, SmFieldCaption, SmFieldbRequired " +
                     "FROM SmFieldInfo WHERE SmDatasetID = ? ORDER BY SmID";
        List<FieldInfo> results = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }
        return results;
    }

    /**
     * 根据数据集 ID 和字段名查询字段元信息。
     *
     * @param datasetId 数据集 ID
     * @param fieldName 字段名
     * @return 字段元信息（如果存在）
     * @throws SQLException 查询失败时抛出
     */
    public Optional<FieldInfo> findByDatasetIdAndFieldName(int datasetId, String fieldName) throws SQLException {
        String sql = "SELECT SmDatasetID, SmFieldName, SmFieldType, SmFieldCaption, SmFieldbRequired " +
                     "FROM SmFieldInfo WHERE SmDatasetID = ? AND SmFieldName = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
            stmt.setString(2, fieldName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    private FieldInfo mapRow(ResultSet rs) throws SQLException {
        int datasetId = rs.getInt("SmDatasetID");
        String fieldName = rs.getString("SmFieldName");
        int fieldTypeValue = rs.getInt("SmFieldType");
        String fieldAlias = rs.getString("SmFieldCaption");
        int requiredValue = rs.getInt("SmFieldbRequired");

        FieldType fieldType = FieldType.fromValue(fieldTypeValue);
        boolean required = requiredValue != 0;

        return new FieldInfo(datasetId, fieldName, fieldType, fieldAlias, required);
    }

    /**
     * 向 SmFieldInfo 中插入单条字段记录。
     *
     * @param field 字段元信息（datasetId 必须已设置）
     * @throws SQLException 操作失败时抛出
     */
    public void insert(FieldInfo field) throws SQLException {
        String sql = "INSERT INTO SmFieldInfo " +
                "(SmDatasetID, SmFieldName, SmFieldType, SmFieldCaption, SmFieldbRequired) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, field.datasetId());
            stmt.setString(2, field.fieldName());
            stmt.setInt(3, field.fieldType().getValue());
            stmt.setString(4, field.fieldAlias());
            stmt.setInt(5, field.required() ? 1 : 0);
            stmt.executeUpdate();
        }
    }

    /**
     * 批量插入字段记录，字段的 datasetId 统一覆盖为 {@code datasetId} 参数。
     *
     * @param datasetId 目标数据集 ID
     * @param fields    字段列表
     * @throws SQLException 操作失败时抛出
     */
    public void insertAll(int datasetId, List<FieldInfo> fields) throws SQLException {
        for (FieldInfo f : fields) {
            insert(new FieldInfo(datasetId, f.fieldName(), f.fieldType(), f.fieldAlias(), f.required()));
        }
    }
}
