package com.supermap.udbx.dataset;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.system.SmRegisterDao;

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
 * 纯属性表数据集实现（DatasetType=Tabular，无几何）。
 *
 * <p>从数据表（如 TabularDT）读取属性记录，无 SmGeometry 列。
 *
 * <p>对应白皮书 §3.1.1（Tabular 数据集）。
 *
 * <p>系统字段（SmID、SmUserID 等以 "Sm" 开头的列）不出现在
 * {@link TabularRecord#attributes()} 中。
 */
public class TabularDataset extends Dataset {

    /** 系统字段前缀：以此开头的列不计入用户属性。 */
    private static final String SYSTEM_COLUMN_PREFIX = "Sm";

    public TabularDataset(Connection conn, DatasetInfo info) {
        super(conn, info);
    }

    /**
     * 读取该数据集中的所有记录。
     *
     * @return 记录列表（按 SmID 升序）
     * @throws RuntimeException 若数据库查询失败
     */
    public List<TabularRecord> getRecords() {
        if (!tableExists()) return List.of();
        String tableName = info.datasetName();
        String sql = "SELECT * FROM \"" + tableName + "\" ORDER BY SmID";
        List<TabularRecord> records = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<String> userColumns = resolveUserColumns(rs.getMetaData());
            while (rs.next()) {
                records.add(mapRow(rs, userColumns));
            }
        } catch (SQLException e) {
            throw new RuntimeException("读取属性表数据集 [" + tableName + "] 失败", e);
        }
        return records;
    }

    /**
     * 根据 SmID 读取单条记录。
     *
     * @param smId 记录 SmID
     * @return 记录对象，不存在时返回 {@code null}
     * @throws RuntimeException 若数据库查询失败
     */
    public TabularRecord getRecord(int smId) {
        String tableName = info.datasetName();
        String sql = "SELECT * FROM \"" + tableName + "\" WHERE SmID = ?";

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
            throw new RuntimeException("读取属性记录 SmID=" + smId + " 失败", e);
        }
    }

    // -----------------------------------------------------------------------
    // 写入方法
    // -----------------------------------------------------------------------

    /**
     * 向属性表中写入一条记录。
     *
     * @param smId       记录 SmID
     * @param attributes 用户属性字段（暂不写入，预留接口）
     * @throws RuntimeException 若写入失败
     */
    public void addRow(int smId, Map<String, Object> attributes) {
        String tableName = info.datasetName();
        List<String> attrKeys = attributes == null ? java.util.List.of() : new ArrayList<>(attributes.keySet());

        StringBuilder sqlBuf = new StringBuilder("INSERT INTO \"").append(tableName)
                .append("\" (SmID, SmUserID");
        for (String key : attrKeys) {
            sqlBuf.append(", \"").append(key).append("\"");
        }
        sqlBuf.append(") VALUES (?, 0");
        for (int i = 0; i < attrKeys.size(); i++) {
            sqlBuf.append(", ?");
        }
        sqlBuf.append(")");

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sqlBuf.toString())) {
                stmt.setInt(1, smId);
                for (int i = 0; i < attrKeys.size(); i++) {
                    stmt.setObject(2 + i, attributes.get(attrKeys.get(i)));
                }
                stmt.executeUpdate();
            }
            new SmRegisterDao(conn).incrementObjectCount(info.datasetId());
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("写入属性记录 SmID=" + smId + " 失败", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void deleteRow(int smId) {
        String tableName = info.datasetName();
        String sql = "DELETE FROM \"" + tableName + "\" WHERE SmID = ?";
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
            throw new RuntimeException("删除属性记录 SmID=" + smId + " 失败: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void updateRow(int smId, Map<String, Object> attributes) {
        String tableName = info.datasetName();
        List<String> attrKeys = attributes == null ? List.of() : new ArrayList<>(attributes.keySet());
        if (attrKeys.isEmpty()) return;

        StringBuilder sqlBuf = new StringBuilder("UPDATE \"").append(tableName).append("\" SET ");
        for (int i = 0; i < attrKeys.size(); i++) {
            if (i > 0) sqlBuf.append(", ");
            sqlBuf.append("\"").append(attrKeys.get(i)).append("\" = ?");
        }
        sqlBuf.append(" WHERE SmID = ?");

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sqlBuf.toString())) {
                for (int i = 0; i < attrKeys.size(); i++) {
                    stmt.setObject(i + 1, attributes.get(attrKeys.get(i)));
                }
                stmt.setInt(attrKeys.size() + 1, smId);
                if (stmt.executeUpdate() == 0) throw new SQLException("SmID=" + smId + " 不存在");
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("更新属性记录 SmID=" + smId + " 失败: " + e.getMessage(), e);
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
     * 将当前行映射为 TabularRecord。
     *
     * @param rs          ResultSet（已定位到目标行）
     * @param userColumns 用户字段列名列表
     */
    private TabularRecord mapRow(ResultSet rs, List<String> userColumns) throws SQLException {
        int smId = rs.getInt("SmID");

        Map<String, Object> attributes = new HashMap<>();
        for (String col : userColumns) {
            attributes.put(col, rs.getObject(col));
        }

        return new TabularRecord(smId, attributes);
    }
}
