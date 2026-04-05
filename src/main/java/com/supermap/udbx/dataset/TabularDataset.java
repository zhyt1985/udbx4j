package com.supermap.udbx.dataset;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.core.QueryOptions;
import com.supermap.udbx.system.SmRegisterDao;

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

/**
 * 纯属性表数据集实现（DatasetKind=Tabular，无几何）。
 *
 * <p>从数据表（如 TabularDT）读取属性记录，无 SmGeometry 列。
 *
 * <p>对应白皮书 §3.1.1（Tabular 数据集）。
 */
public class TabularDataset extends Dataset {

    private static final String SYSTEM_COLUMN_PREFIX = "Sm";

    public TabularDataset(Connection conn, DatasetInfo info) {
        super(conn, info);
    }

    /**
     * 读取该数据集中的所有记录。
     */
    public List<TabularRecord> list() {
        return list(QueryOptions.EMPTY);
    }

    /**
     * 按查询选项读取记录。
     */
    public List<TabularRecord> list(QueryOptions options) {
        if (!tableExists()) return List.of();
        var sqlBuilder = new StringBuilder("SELECT * FROM \"").append(info.name()).append("\"");
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

        List<TabularRecord> records = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<String> userColumns = resolveUserColumns(rs.getMetaData());
                while (rs.next()) {
                    records.add(mapRow(rs, userColumns));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("读取属性表数据集 [" + info.name() + "] 失败", e);
        }
        return records;
    }

    /**
     * 根据 id 读取单条记录。
     */
    public TabularRecord getById(int id) {
        String sql = "SELECT * FROM \"" + info.name() + "\" WHERE SmID = ?";

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
            throw new RuntimeException("读取属性记录 id=" + id + " 失败", e);
        }
    }

    /**
     * 查询数据集中的记录总数。
     */
    public int count() {
        String sql = "SELECT COUNT(*) FROM \"" + info.name() + "\"";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("查询记录数量失败", e);
        }
    }

    // -----------------------------------------------------------------------
    // 写入方法
    // -----------------------------------------------------------------------

    /**
     * 向属性表中写入一条记录。
     */
    public void insert(TabularRecord record) {
        Map<String, Object> attributes = record.attributes();
        List<String> attrKeys = attributes == null || attributes.isEmpty()
                ? java.util.List.of()
                : new ArrayList<>(attributes.keySet());

        StringBuilder sqlBuf = new StringBuilder("INSERT INTO \"").append(info.name())
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
                stmt.setInt(1, record.id());
                for (int i = 0; i < attrKeys.size(); i++) {
                    stmt.setObject(2 + i, attributes.get(attrKeys.get(i)));
                }
                stmt.executeUpdate();
            }
            new SmRegisterDao(conn).incrementObjectCount(info.id());
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("写入属性记录 id=" + record.id() + " 失败", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * 批量写入记录（高性能）。
     */
    public void insertMany(List<TabularRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO \"" + info.name()
                + "\" (SmID, SmUserID) VALUES (?, 0)";

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (TabularRecord r : records) {
                    ps.setInt(1, r.id());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            new SmRegisterDao(conn).incrementObjectCountBatch(
                info.id(), records.size(), 0);
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("批量写入失败", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * 删除指定 id 的记录。
     */
    public void delete(int id) {
        String sql = "DELETE FROM \"" + info.name() + "\" WHERE SmID = ?";
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
            throw new RuntimeException("删除属性记录 id=" + id + " 失败: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * 更新指定 id 的记录。
     */
    public void update(int id, Map<String, Object> attributes) {
        List<String> attrKeys = attributes == null ? List.of() : new ArrayList<>(attributes.keySet());
        if (attrKeys.isEmpty()) return;

        StringBuilder sqlBuf = new StringBuilder("UPDATE \"").append(info.name()).append("\" SET ");
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
                stmt.setInt(attrKeys.size() + 1, id);
                int affected = stmt.executeUpdate();
                if (affected == 0) {
                    throw new SQLException("id=" + id + " 不存在");
                }
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("更新属性记录 id=" + id + " 失败: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // -----------------------------------------------------------------------
    // 私有辅助方法
    // -----------------------------------------------------------------------

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

    private TabularRecord mapRow(ResultSet rs, List<String> userColumns) throws SQLException {
        int id = rs.getInt("SmID");

        Map<String, Object> attributes = new HashMap<>();
        for (String col : userColumns) {
            attributes.put(col, rs.getObject(col));
        }

        return new TabularRecord(id, attributes);
    }
}
