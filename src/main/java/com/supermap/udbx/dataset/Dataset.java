package com.supermap.udbx.dataset;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.core.DatasetType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 数据集抽象基类。
 *
 * <p>封装数据集元信息（DatasetInfo）和数据库连接。
 * 子类通过 {@code conn} 访问数据集数据表。
 *
 * <p>遵循 AutoCloseable 协议，但 Connection 生命周期由 {@link com.supermap.udbx.UdbxDataSource} 管理。
 */
public abstract class Dataset implements AutoCloseable {

    protected final Connection conn;
    protected final DatasetInfo info;

    protected Dataset(Connection conn, DatasetInfo info) {
        this.conn = conn;
        this.info = info;
    }

    /**
     * 返回数据集元信息（来自 SmRegister 系统表）。
     */
    public DatasetInfo getInfo() {
        return info;
    }

    /**
     * 返回数据集名称（SmDatasetName）。
     */
    public String getName() {
        return info.datasetName();
    }

    /**
     * 返回数据集类型（SmDatasetType）。
     */
    public DatasetType getType() {
        return info.datasetType();
    }

    /**
     * 返回数据集要素数量（SmObjectCount）。
     */
    public int getObjectCount() {
        return info.objectCount();
    }

    /**
     * 返回坐标系 ID（SmSRID）。
     */
    public int getSrid() {
        return info.srid();
    }

    /**
     * 返回数据集 ID（SmDatasetID）。
     */
    public int getDatasetId() {
        return info.datasetId();
    }

    /**
     * Dataset 本身不持有需要关闭的资源；Connection 由 UdbxDataSource 管理。
     * 子类可覆盖此方法以释放自身资源。
     */
    @Override
    public void close() {
        // 默认不操作；Connection 由 UdbxDataSource 统一关闭
    }

    /**
     * 检查该数据集对应的 SQLite 物理表是否存在。
     *
     * <p>某些 UDBX 文件中，SmRegister 记录了数据集元信息但实际数据表缺失（"幽灵表"），
     * 调用本方法可在查询前进行预检，避免抛出 "no such table" 异常。
     *
     * @return {@code true} 表示物理表存在；{@code false} 表示不存在或查询失败
     */
    protected boolean tableExists() {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, info.datasetName());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }
}
