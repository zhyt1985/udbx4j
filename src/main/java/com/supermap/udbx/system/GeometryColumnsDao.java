package com.supermap.udbx.system;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * geometry_columns 系统表的数据访问对象。
 *
 * <p>SpatiaLite 标准表，存储矢量数据集的几何列信息。
 */
public class GeometryColumnsDao {

    private final Connection conn;

    public GeometryColumnsDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * 获取指定表的几何类型。
     *
     * @param tableName 表名（小写）
     * @return 几何类型值（如果存在）
     * @throws SQLException 查询失败时抛出
     */
    public Optional<Integer> getGeometryType(String tableName) throws SQLException {
        String sql = "SELECT geometry_type FROM geometry_columns WHERE f_table_name = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt("geometry_type"));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 获取指定表的 SRID。
     *
     * @param tableName 表名（小写）
     * @return SRID（如果存在）
     * @throws SQLException 查询失败时抛出
     */
    public Optional<Integer> getSrid(String tableName) throws SQLException {
        String sql = "SELECT srid FROM geometry_columns WHERE f_table_name = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt("srid"));
                }
            }
        }
        return Optional.empty();
    }
}
