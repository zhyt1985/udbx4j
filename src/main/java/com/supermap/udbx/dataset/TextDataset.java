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
 * 文本数据集实现（DatasetKind=TEXT=7）。
 *
 * <p>从数据表读取文本要素，几何数据存储于 SmGeometry 列（GeoText 格式 BLOB）。
 * 属性数据从数据表的用户字段列读取。
 *
 * <p>对应白皮书 §3.1.5（文本数据集）和 §4.4（文本对象存储结构）。
 *
 * <p><b>TODO:</b> GeoText 二进制解析器待实现（当前返回占位数据）。
 */
public class TextDataset extends VectorDataset {

    private static final String GEOMETRY_COLUMN = "SmGeometry";
    private static final String SYSTEM_COLUMN_PREFIX = "Sm";

    public TextDataset(Connection conn, DatasetInfo info) {
        super(conn, info);
    }

    /**
     * 读取该数据集中的所有文本要素。
     */
    public List<TextFeature> list() {
        return list(QueryOptions.EMPTY);
    }

    /**
     * 按查询选项读取文本要素。
     */
    public List<TextFeature> list(QueryOptions options) {
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

        List<TextFeature> features = new ArrayList<>();

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
            throw new RuntimeException("读取文本数据集 [" + getName() + "] 失败", e);
        }
        return features;
    }

    /**
     * 根据 id 读取单个文本要素。
     */
    public TextFeature getById(int id) {
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
            throw new RuntimeException("读取文本要素 id=" + id + " 失败", e);
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

    // -----------------------------------------------------------------------
    // 写入方法
    // -----------------------------------------------------------------------

    /**
     * 批量写入文本要素（高性能）。
     *
     * <p><b>TODO:</b> GeoText 二进制编码器待实现。
     */
    public void insertMany(List<TextFeature> features) {
        if (features == null || features.isEmpty()) {
            return;
        }

        // TODO: 实现 GeoText 二进制编码
        throw new UnsupportedOperationException("TextDataset.insertMany() 待实现：需要 GeoText 二进制编码器");
    }

    /**
     * 向文本数据集中写入一个要素。
     *
     * <p><b>TODO:</b> GeoText 二进制编码器待实现。
     */
    public void insert(TextFeature feature) {
        // TODO: 实现 GeoText 二进制编码
        throw new UnsupportedOperationException("TextDataset.insert() 待实现：需要 GeoText 二进制编码器");
    }

    /**
     * 删除指定 id 的文本要素。
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
            throw new RuntimeException("删除文本要素 id=" + id + " 失败: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * 更新指定 id 的文本要素。
     *
     * <p><b>TODO:</b> GeoText 二进制编码器待实现。
     */
    public void update(int id, String text, double x, double y, Map<String, Object> attributes) {
        // TODO: 实现 GeoText 二进制编码
        throw new UnsupportedOperationException("TextDataset.update() 待实现：需要 GeoText 二进制编码器");
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

    private TextFeature mapRow(ResultSet rs, List<String> userColumns) throws SQLException {
        int id = rs.getInt("SmID");
        byte[] geomBytes = rs.getBytes(GEOMETRY_COLUMN);

        // TODO: 使用 GeoTextReader 解析二进制数据
        // 临时返回占位数据（实际实现需要解析 GeoText BLOB）
        String text = geomBytes != null ? "[GeoText: " + geomBytes.length + " bytes]" : "";
        double x = 0.0;
        double y = 0.0;

        Map<String, Object> attributes = new HashMap<>();
        for (String col : userColumns) {
            attributes.put(col, rs.getObject(col));
        }

        return new TextFeature(id, text, x, y, attributes);
    }
}
