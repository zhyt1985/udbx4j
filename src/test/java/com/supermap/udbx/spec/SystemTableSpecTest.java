package com.supermap.udbx.spec;

import com.supermap.udbx.system.GeometryColumnsDao;
import com.supermap.udbx.system.SmDataSourceInfoDao;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 测试：验证数据源级系统表读取。
 *
 * <p>包含：
 * <ul>
 *   <li>SmDataSourceInfo —— 数据源元信息（白皮书 §2 节）</li>
 *   <li>geometry_columns —— SpatiaLite 矢量数据集注册表</li>
 * </ul>
 *
 * <p>测试数据（SampleData.udbx 中的已知值）：
 * <pre>
 *   SmDataSourceInfo.SmVersion = 0
 *   SmDataSourceInfo.SmDataFormat = 1
 *   geometry_columns[basemap_p].geometry_type = 1 (POINT)
 *   geometry_columns[basemap_p].srid = 4326
 * </pre>
 */
class SystemTableSpecTest {

    private static final Path SAMPLE_DATA = Paths.get("src/test/resources/SampleData.udbx");

    // ── SmDataSourceInfo ────────────────────────────────────────────────────

    @Test
    void should_read_datasource_version() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmDataSourceInfoDao dao = new SmDataSourceInfoDao(conn);

            Optional<Integer> version = dao.getVersion();

            assertThat(version).isPresent();
            assertThat(version.get()).isEqualTo(0);
        }
    }

    @Test
    void should_read_datasource_data_format() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmDataSourceInfoDao dao = new SmDataSourceInfoDao(conn);

            Optional<Integer> dataFormat = dao.getDataFormat();

            assertThat(dataFormat).isPresent();
            assertThat(dataFormat.get()).isEqualTo(1);
        }
    }

    // ── geometry_columns ───────────────────────────────────────────────────

    @Test
    void should_read_basemap_p_geometry_type() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            GeometryColumnsDao dao = new GeometryColumnsDao(conn);

            Optional<Integer> geometryType = dao.getGeometryType("basemap_p");

            assertThat(geometryType).isPresent();
            assertThat(geometryType.get()).isEqualTo(1); // POINT
        }
    }

    @Test
    void should_read_basemap_p_srid() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            GeometryColumnsDao dao = new GeometryColumnsDao(conn);

            Optional<Integer> srid = dao.getSrid("basemap_p");

            assertThat(srid).isPresent();
            assertThat(srid.get()).isEqualTo(4326);
        }
    }

    @Test
    void should_read_basemap_l_geometry_type() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            GeometryColumnsDao dao = new GeometryColumnsDao(conn);

            Optional<Integer> geometryType = dao.getGeometryType("basemap_l");

            assertThat(geometryType).isPresent();
            assertThat(geometryType.get()).isEqualTo(5); // MULTILINESTRING
        }
    }

    @Test
    void should_read_basemap_r_geometry_type() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            GeometryColumnsDao dao = new GeometryColumnsDao(conn);

            Optional<Integer> geometryType = dao.getGeometryType("basemap_r");

            assertThat(geometryType).isPresent();
            assertThat(geometryType.get()).isEqualTo(6); // MULTIPOLYGON
        }
    }

    @Test
    void should_read_3d_point_geometry_type() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            GeometryColumnsDao dao = new GeometryColumnsDao(conn);

            Optional<Integer> geometryType = dao.getGeometryType("basemap_pz");

            assertThat(geometryType).isPresent();
            assertThat(geometryType.get()).isEqualTo(1001); // POINT Z
        }
    }

    @Test
    void should_return_empty_for_nonexistent_table() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            GeometryColumnsDao dao = new GeometryColumnsDao(conn);

            Optional<Integer> geometryType = dao.getGeometryType("nonexistent");

            assertThat(geometryType).isEmpty();
        }
    }
}
