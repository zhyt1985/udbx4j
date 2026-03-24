package com.supermap.udbx.streaming;

import com.supermap.udbx.core.DatasetInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 * 流式读取数据集要素的 Spliterator。
 *
 * <p>基于 JDBC ResultSet 实现懒加载迭代，避免一次性加载所有数据到内存。
 * 使用 PreparedStatement 防止 SQL 注入。
 *
 * <p>生命周期管理：
 * <ul>
 *   <li>构造时执行 SQL 查询</li>
 *   <li>每次 {@link #tryAdvance(Consumer)} 读取一行</li>
 *   <li>读取完毕或发生异常时自动关闭 ResultSet 和 Statement</li>
 *   <li>显式调用 {@link #close()} 可提前释放资源</li>
 * </ul>
 *
 * @param <T> 要素类型（如 PointFeature、LineFeature 等）
 */
public class FeatureSpliterator<T> extends Spliterators.AbstractSpliterator<T> implements AutoCloseable {

    private final PreparedStatement stmt;
    private final ResultSet rs;
    private final Connection conn;
    private final DatasetInfo info;
    private final RowMapper<T> rowMapper;
    private boolean closed = false;

    /**
     * 行映射器接口：将 ResultSet 当前行映射为要素对象。
     *
     * @param <T> 要素类型
     */
    @FunctionalInterface
    public interface RowMapper<T> {
        /**
         * 映射当前行。
         *
         * @param rs ResultSet（已定位到目标行）
         * @return 要素对象
         * @throws SQLException 若读取失败
         */
        T map(ResultSet rs) throws SQLException;
    }

    /**
     * 创建 FeatureSpliterator。
     *
     * @param conn      数据库连接
     * @param info      数据集元信息
     * @param tableName 数据表名称（将进行 SQL 转义，防止注入）
     * @param rowMapper 行映射器
     * @throws SQLException 若查询执行失败
     */
    public FeatureSpliterator(
            Connection conn,
            DatasetInfo info,
            String tableName,
            RowMapper<T> rowMapper) throws SQLException {
        super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);
        this.conn = conn;
        this.info = info;
        this.rowMapper = rowMapper;

        // 使用 PreparedStatement 参数化查询，防止 SQL 注入
        // 表名通过转义处理，字段名使用硬编码（不支持用户输入）
        String sql = "SELECT * FROM \"" + escapeTableName(tableName) + "\" ORDER BY SmID";
        this.stmt = conn.prepareStatement(sql);
        this.rs = stmt.executeQuery();
    }

    /**
     * 转义表名，防止 SQL 注入。
     *
     * <p>SQLite 转义规则：双引号内嵌双引号通过两个双引号表示。
     *
     * @param tableName 原始表名
     * @return 转义后的表名
     */
    private static String escapeTableName(String tableName) {
        return tableName.replace("\"", "\"\"");
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
        if (closed) {
            throw new IllegalStateException("Spliterator 已关闭");
        }

        try {
            if (rs.next()) {
                T feature = rowMapper.map(rs);
                action.accept(feature);
                return true;
            }
            // 读取完毕，自动关闭资源
            close();
            return false;
        } catch (SQLException e) {
            // 发生异常，关闭资源并抛出 RuntimeException
            close();
            throw new RuntimeException("读取数据集 [" + info.datasetName() + "] 失败", e);
        }
    }

    /**
     * 关闭 ResultSet 和 Statement，释放数据库资源。
     *
     * <p>可重复调用，已关闭时不会抛出异常。
     */
    public void close() {
        if (!closed) {
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (SQLException e) {
                // 记录警告，但不中断
            } finally {
                try {
                    if (stmt != null) {
                        stmt.close();
                    }
                } catch (SQLException e) {
                    // 记录警告，但不中断
                } finally {
                    closed = true;
                }
            }
        }
    }
}
