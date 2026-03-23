package com.supermap.udbx.spec;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.system.SmDataSourceInfoDao;
import com.supermap.udbx.system.SmRegisterDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 测试：验证 UdbxDataSource.create() 正确初始化空 UDBX 文件。
 *
 * <p>对应白皮书 §2 节系统表定义，验证：
 * <ul>
 *   <li>所有必要系统表已创建</li>
 *   <li>SmDataSourceInfo 已写入初始记录（version=0, dataFormat=1）</li>
 *   <li>spatial_ref_sys 已写入 SRID=4326（WGS84）</li>
 *   <li>SmRegister 初始为空</li>
 * </ul>
 */
class CreateDataSourceSpecTest {

    @Test
    void create_must_initialize_all_system_tables(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("test.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            // 创建后立即关闭，用原始 Connection 验证表结构
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            assertThat(tableExists(conn, "SmDataSourceInfo")).isTrue();
            assertThat(tableExists(conn, "SmRegister")).isTrue();
            assertThat(tableExists(conn, "SmFieldInfo")).isTrue();
            assertThat(tableExists(conn, "geometry_columns")).isTrue();
            assertThat(tableExists(conn, "spatial_ref_sys")).isTrue();
        }
    }

    @Test
    void create_must_write_datasourceinfo_record(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("test.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {}

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            SmDataSourceInfoDao dao = new SmDataSourceInfoDao(conn);
            assertThat(dao.getVersion()).isPresent();
            assertThat(dao.getVersion().get()).isEqualTo(0);
            assertThat(dao.getDataFormat()).isPresent();
            assertThat(dao.getDataFormat().get()).isEqualTo(1);
        }
    }

    @Test
    void create_must_insert_wgs84_into_spatial_ref_sys(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("test.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {}

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
             var stmt = conn.prepareStatement(
                     "SELECT srid, auth_name FROM spatial_ref_sys WHERE srid = 4326");
             ResultSet rs = stmt.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("srid")).isEqualTo(4326);
            assertThat(rs.getString("auth_name")).isEqualTo("epsg");
        }
    }

    @Test
    void create_must_have_empty_smart_register(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("test.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {}

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            SmRegisterDao dao = new SmRegisterDao(conn);
            assertThat(dao.findAll()).isEmpty();
        }
    }

    @Test
    void create_must_fail_if_file_already_exists(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("existing.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {}

        // 第二次 create 应抛出异常（文件已存在）
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> UdbxDataSource.create(path));
    }

    // -----------------------------------------------------------------------
    // 辅助方法
    // -----------------------------------------------------------------------

    private boolean tableExists(Connection conn, String tableName) throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
