package com.supermap.udbx;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 0 验证测试：确认项目骨架就绪。
 *
 * <p>验证内容：
 * <ul>
 *   <li>SampleData.udbx 存在于 test/resources</li>
 *   <li>sqlite-jdbc 能成功打开 UDBX 文件</li>
 *   <li>可以查询 SmRegister 系统表（存在且有数据）</li>
 * </ul>
 */
class Phase0SanityTest {

    private static final String SAMPLE_DB_RESOURCE = "/SampleData.udbx";

    @Test
    void sample_data_file_must_exist_in_test_resources() {
        URL resource = getClass().getResource(SAMPLE_DB_RESOURCE);

        assertThat(resource)
            .as("SampleData.udbx should be present in src/test/resources")
            .isNotNull();
    }

    @Test
    void sqlite_jdbc_must_open_udbx_file() throws Exception {
        URL resource = getClass().getResource(SAMPLE_DB_RESOURCE);
        String url = "jdbc:sqlite:" + resource.getPath();

        try (Connection conn = DriverManager.getConnection(url)) {
            assertThat(conn.isClosed()).isFalse();
        }
    }

    @Test
    void udbx_file_must_contain_SmRegister_table() throws Exception {
        URL resource = getClass().getResource(SAMPLE_DB_RESOURCE);
        String url = "jdbc:sqlite:" + resource.getPath();

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM SmRegister")) {

            assertThat(rs.next()).isTrue();
            int count = rs.getInt(1);
            assertThat(count)
                .as("SmRegister should have at least one dataset")
                .isGreaterThan(0);
        }
    }

    @Test
    void udbx_file_must_contain_spatial_ref_sys_table() throws Exception {
        URL resource = getClass().getResource(SAMPLE_DB_RESOURCE);
        String url = "jdbc:sqlite:" + resource.getPath();

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM spatial_ref_sys")) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isGreaterThan(0);
        }
    }
}
