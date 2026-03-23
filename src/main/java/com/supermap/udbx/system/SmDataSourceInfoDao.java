package com.supermap.udbx.system;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * SmDataSourceInfo 系统表的数据访问对象。
 *
 * <p>对应白皮书 §2 节数据源信息表。
 * 提供对数据源元信息的查询操作。
 */
public class SmDataSourceInfoDao {

    private final Connection conn;

    public SmDataSourceInfoDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * 获取数据源版本号。
     *
     * @return 版本号（如果存在）
     * @throws SQLException 查询失败时抛出
     */
    public Optional<Integer> getVersion() throws SQLException {
        String sql = "SELECT SmVersion FROM SmDataSourceInfo LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return Optional.of(rs.getInt("SmVersion"));
            }
        }
        return Optional.empty();
    }

    /**
     * 获取数据格式。
     *
     * @return 数据格式（如果存在）
     * @throws SQLException 查询失败时抛出
     */
    public Optional<Integer> getDataFormat() throws SQLException {
        String sql = "SELECT SmDataFormat FROM SmDataSourceInfo LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return Optional.of(rs.getInt("SmDataFormat"));
            }
        }
        return Optional.empty();
    }
}
