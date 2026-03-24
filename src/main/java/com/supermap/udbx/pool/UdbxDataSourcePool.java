package com.supermap.udbx.pool;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.core.DatasetType;
import com.supermap.udbx.dataset.*;
import com.supermap.udbx.system.SmRegisterDao;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Function;

/**
 * UDBX 数据源连接池（基于 HikariCP）。
 *
 * <p>提供连接池管理和数据集访问功能，支持高并发读写场景。
 *
 * <p>SQLite 优化配置：
 * <ul>
 *   <li>WAL 模式（Write-Ahead Logging）- 提升并发读写性能</li>
 *   <li>共享缓存模式 - 多连接共享数据页缓存</li>
 *   <li>NORMAL 同步模式 - 平衡性能与安全性</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * try (UdbxDataSourcePool pool = new UdbxDataSourcePool("/path/to/data.udbx", 10)) {
 *     PointDataset dataset = pool.getDataset("BaseMap_P");
 *     List<PointFeature> features = dataset.getFeatures();
 * }
 * }</pre>
 */
public class UdbxDataSourcePool implements AutoCloseable {

    private final HikariDataSource pool;

    /**
     * 创建 UDBX 数据源连接池。
     *
     * @param path     UDBX 文件路径（.udbx 文件）
     * @param poolSize 连接池最大大小
     */
    public UdbxDataSourcePool(String path, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + path);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);

        // SQLite 优化配置
        config.addDataSourceProperty("cache", "shared");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");

        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        this.pool = new HikariDataSource(config);
        checkWALEnabled();
    }

    /**
     * 执行数据库操作（自动管理连接）。
     *
     * @param callback 回调函数，接收 Connection 并返回结果
     * @param <T>      返回类型
     * @return 回调函数的返回值
     * @throws RuntimeException 若数据库操作失败
     */
    public <T> T execute(Function<Connection, T> callback) {
        try (Connection conn = pool.getConnection()) {
            return callback.apply(conn);
        } catch (SQLException e) {
            throw new RuntimeException("数据库操作失败", e);
        }
    }

    /**
     * 获取指定名称的数据集。
     *
     * @param name 数据集名称（SmDatasetName）
     * @return 数据集实例（PointDataset、LineDataset、RegionDataset、TabularDataset 等）
     * @throws IllegalArgumentException 若数据集不存在
     * @throws UnsupportedOperationException 若数据集类型不支持
     */
    public Dataset getDataset(String name) {
        return execute(conn -> {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT DatasetType FROM SmRegister WHERE DatasetName = '" + name + "'")) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("数据集不存在: " + name);
                }
                int datasetType = rs.getInt("DatasetType");

                SmRegisterDao dao = new SmRegisterDao(conn);
                DatasetInfo info = dao.findByName(name)
                    .orElseThrow(() -> new IllegalArgumentException("数据集不存在: " + name));

                return switch (info.datasetType()) {
                    case POINT -> new PointDataset(conn, info);
                    case LINE -> new LineDataset(conn, info);
                    case REGION -> new RegionDataset(conn, info);
                    case TABULAR -> new TabularDataset(conn, info);
                    default -> throw new UnsupportedOperationException(
                        "不支持的数据集类型: " + info.datasetType());
                };
            }
        });
    }

    /**
     * 检查 WAL 模式是否启用（启动时验证）。
     *
     * <p>若 WAL 模式未启用，会打印警告信息。WAL 模式对并发性能至关重要。
     */
    private void checkWALEnabled() {
        execute(conn -> {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                if (rs.next()) {
                    String mode = rs.getString(1);
                    if (!"wal".equalsIgnoreCase(mode)) {
                        System.err.println(
                            "警告: WAL 模式未启用，并发性能受限。当前模式: " + mode);
                    }
                }
            }
            return null;
        });
    }

    /**
     * 关闭连接池（释放所有资源）。
     */
    @Override
    public void close() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }
}
