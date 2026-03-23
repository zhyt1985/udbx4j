package com.supermap.udbx.spec;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.core.DatasetType;
import com.supermap.udbx.core.FieldInfo;
import com.supermap.udbx.core.FieldType;
import com.supermap.udbx.dataset.CadDataset;
import com.supermap.udbx.dataset.Dataset;
import com.supermap.udbx.dataset.LineDataset;
import com.supermap.udbx.dataset.LineZDataset;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.dataset.PointZDataset;
import com.supermap.udbx.dataset.RegionDataset;
import com.supermap.udbx.dataset.RegionZDataset;
import com.supermap.udbx.dataset.TabularDataset;
import com.supermap.udbx.system.SmRegisterDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 测试：验证 UdbxDataSource 能够正确创建和打开所有 8 种数据集类型。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code create()} + 创建 Point / PointZ / Line / LineZ / Region / RegionZ / Tabular / CAD</li>
 *   <li>{@code open()} + {@code getDataset()} 返回正确实例类型</li>
 *   <li>{@code getDataset()} 名称不存在时返回 null</li>
 *   <li>通过 SmRegisterDao 查询所有数据集列表</li>
 *   <li>创建数据集时附带用户字段</li>
 * </ul>
 */
class UdbxDataSourceAllTypesSpecTest {

    // ── create + 直接实例类型验证 ─────────────────────────────────────────────

    @Test
    void create_point_dataset_returns_point_dataset_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("point.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointDataset result = ds.createPointDataset("MyPoints", 4326);
            assertThat(result).isInstanceOf(PointDataset.class);
        }
    }

    @Test
    void create_pointz_dataset_returns_pointz_dataset_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("pointz.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointZDataset result = ds.createPointZDataset("MyPointsZ", 4326);
            assertThat(result).isInstanceOf(PointZDataset.class);
        }
    }

    @Test
    void create_line_dataset_returns_line_dataset_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("line.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineDataset result = ds.createLineDataset("MyLines", 4326);
            assertThat(result).isInstanceOf(LineDataset.class);
        }
    }

    @Test
    void create_linez_dataset_returns_linez_dataset_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("linez.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineZDataset result = ds.createLineZDataset("MyLinesZ", 4326);
            assertThat(result).isInstanceOf(LineZDataset.class);
        }
    }

    @Test
    void create_region_dataset_returns_region_dataset_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("region.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionDataset result = ds.createRegionDataset("MyRegions", 4326);
            assertThat(result).isInstanceOf(RegionDataset.class);
        }
    }

    @Test
    void create_regionz_dataset_returns_regionz_dataset_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("regionz.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionZDataset result = ds.createRegionZDataset("MyRegionsZ", 4326);
            assertThat(result).isInstanceOf(RegionZDataset.class);
        }
    }

    @Test
    void create_tabular_dataset_returns_tabular_dataset_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("tabular.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            TabularDataset result = ds.createTabularDataset("MyTable");
            assertThat(result).isInstanceOf(TabularDataset.class);
        }
    }

    @Test
    void create_cad_dataset_returns_cad_dataset_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            CadDataset result = ds.createCadDataset("MyCad");
            assertThat(result).isInstanceOf(CadDataset.class);
        }
    }

    // ── open() + getDataset() 返回正确实例类型 ────────────────────────────────

    @Test
    void open_and_get_point_dataset_returns_point_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("pt.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createPointDataset("Pts", 4326);
        }
        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            Dataset result = ds.getDataset("Pts");
            assertThat(result).isInstanceOf(PointDataset.class);
        }
    }

    @Test
    void open_and_get_pointz_dataset_returns_pointz_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("ptz.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createPointZDataset("PtsZ", 4326);
        }
        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            Dataset result = ds.getDataset("PtsZ");
            assertThat(result).isInstanceOf(PointZDataset.class);
        }
    }

    @Test
    void open_and_get_line_dataset_returns_line_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("ln.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createLineDataset("Lines", 4326);
        }
        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            Dataset result = ds.getDataset("Lines");
            assertThat(result).isInstanceOf(LineDataset.class);
        }
    }

    @Test
    void open_and_get_linez_dataset_returns_linez_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("lnz.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createLineZDataset("LinesZ", 4326);
        }
        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            Dataset result = ds.getDataset("LinesZ");
            assertThat(result).isInstanceOf(LineZDataset.class);
        }
    }

    @Test
    void open_and_get_region_dataset_returns_region_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("rg.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createRegionDataset("Regions", 4326);
        }
        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            Dataset result = ds.getDataset("Regions");
            assertThat(result).isInstanceOf(RegionDataset.class);
        }
    }

    @Test
    void open_and_get_regionz_dataset_returns_regionz_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("rgz.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createRegionZDataset("RegionsZ", 4326);
        }
        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            Dataset result = ds.getDataset("RegionsZ");
            assertThat(result).isInstanceOf(RegionZDataset.class);
        }
    }

    @Test
    void open_and_get_tabular_dataset_returns_tabular_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("tab.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createTabularDataset("Tabular");
        }
        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            Dataset result = ds.getDataset("Tabular");
            assertThat(result).isInstanceOf(TabularDataset.class);
        }
    }

    @Test
    void open_and_get_cad_dataset_returns_cad_instance(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createCadDataset("Cad");
        }
        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            Dataset result = ds.getDataset("Cad");
            assertThat(result).isInstanceOf(CadDataset.class);
        }
    }

    // ── getDataset() 不存在时返回 null ────────────────────────────────────────

    @Test
    void get_dataset_returns_null_for_nonexistent_name(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("empty.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            Dataset result = ds.getDataset("DoesNotExist");
            assertThat(result).isNull();
        }
    }

    // ── 通过 SmRegisterDao 查询所有数据集列表 ─────────────────────────────────

    @Test
    void created_datasets_are_registered_in_smregister(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("all.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createPointDataset("Pts", 4326);
            ds.createLineDataset("Lines", 4326);
            ds.createRegionDataset("Regions", 4326);
            ds.createTabularDataset("Tabular");
            ds.createCadDataset("Cad");
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            SmRegisterDao dao = new SmRegisterDao(conn);
            assertThat(dao.findAll()).hasSize(5);
        }
    }

    @Test
    void all_eight_dataset_types_registered_with_correct_type_values(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("eight.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createPointDataset("Pt", 4326);
            ds.createPointZDataset("PtZ", 4326);
            ds.createLineDataset("Ln", 4326);
            ds.createLineZDataset("LnZ", 4326);
            ds.createRegionDataset("Rg", 4326);
            ds.createRegionZDataset("RgZ", 4326);
            ds.createTabularDataset("Tab");
            ds.createCadDataset("Cad");
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            SmRegisterDao dao = new SmRegisterDao(conn);
            assertThat(dao.findAll()).hasSize(8);
            assertThat(dao.findByName("Pt").get().datasetType()).isEqualTo(DatasetType.Point);
            assertThat(dao.findByName("PtZ").get().datasetType()).isEqualTo(DatasetType.PointZ);
            assertThat(dao.findByName("Ln").get().datasetType()).isEqualTo(DatasetType.Line);
            assertThat(dao.findByName("LnZ").get().datasetType()).isEqualTo(DatasetType.LineZ);
            assertThat(dao.findByName("Rg").get().datasetType()).isEqualTo(DatasetType.Region);
            assertThat(dao.findByName("RgZ").get().datasetType()).isEqualTo(DatasetType.RegionZ);
            assertThat(dao.findByName("Tab").get().datasetType()).isEqualTo(DatasetType.Tabular);
            assertThat(dao.findByName("Cad").get().datasetType()).isEqualTo(DatasetType.CAD);
        }
    }

    // ── 创建数据集时附带用户字段 ──────────────────────────────────────────────

    @Test
    void create_point_dataset_with_fields_registers_fields_in_smfieldinfo(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("fields.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "Name", FieldType.NText, "名称", false),
                new FieldInfo(0, "Value", FieldType.Double, "数值", false)
        );
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createPointDataset("PtsWithFields", 4326, fields);
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
             var stmt = conn.prepareStatement(
                     "SELECT COUNT(*) AS cnt FROM SmFieldInfo WHERE SmDatasetID = 1");
             var rs = stmt.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("cnt")).isEqualTo(2);
        }
    }

    @Test
    void create_tabular_dataset_with_fields_creates_columns(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("tabfields.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "Code", FieldType.Int32, "编码", false),
                new FieldInfo(0, "Label", FieldType.NText, "标签", false)
        );
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createTabularDataset("TabWithFields", fields);
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
             var stmt = conn.prepareStatement(
                     "SELECT COUNT(*) AS cnt FROM SmFieldInfo WHERE SmDatasetID = 1");
             var rs = stmt.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("cnt")).isEqualTo(2);
        }
    }

    @Test
    void create_cad_dataset_with_fields_registers_fields(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("cadfields.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "Layer", FieldType.NText, "图层", false)
        );
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createCadDataset("CadWithFields", fields);
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
             var stmt = conn.prepareStatement(
                     "SELECT COUNT(*) AS cnt FROM SmFieldInfo WHERE SmDatasetID = 1");
             var rs = stmt.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("cnt")).isEqualTo(1);
        }
    }

    // ── SRID 正确传递到 SmRegister ────────────────────────────────────────────

    @Test
    void created_vector_dataset_srid_is_stored_correctly(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("srid.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createPointDataset("PtSrid", 4326);
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            SmRegisterDao dao = new SmRegisterDao(conn);
            assertThat(dao.findByName("PtSrid").get().srid()).isEqualTo(4326);
        }
    }

    @Test
    void created_tabular_dataset_srid_is_zero(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("tabsrid.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createTabularDataset("TabSrid");
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            SmRegisterDao dao = new SmRegisterDao(conn);
            assertThat(dao.findByName("TabSrid").get().srid()).isEqualTo(0);
        }
    }

    // ── geometry_columns 注册验证 ─────────────────────────────────────────────

    @Test
    void create_point_dataset_registers_in_geometry_columns(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("geocol.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createPointDataset("GcPts", 4326);
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
             var stmt = conn.prepareStatement(
                     "SELECT geometry_type, srid FROM geometry_columns WHERE f_table_name = 'gcpts'");
             var rs = stmt.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("geometry_type")).isEqualTo(1);   // GAIA Point
            assertThat(rs.getInt("srid")).isEqualTo(4326);
        }
    }

    @Test
    void create_line_dataset_registers_correct_geometry_type_in_geometry_columns(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("geocol_line.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createLineDataset("GcLines", 4326);
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
             var stmt = conn.prepareStatement(
                     "SELECT geometry_type FROM geometry_columns WHERE f_table_name = 'gclines'");
             var rs = stmt.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("geometry_type")).isEqualTo(5);   // GAIA MultiLineString
        }
    }

    @Test
    void create_region_dataset_registers_correct_geometry_type_in_geometry_columns(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("geocol_region.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createRegionDataset("GcRegions", 4326);
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
             var stmt = conn.prepareStatement(
                     "SELECT geometry_type FROM geometry_columns WHERE f_table_name = 'gcregions'");
             var rs = stmt.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("geometry_type")).isEqualTo(6);   // GAIA MultiPolygon
        }
    }

    @Test
    void cad_dataset_is_not_registered_in_geometry_columns(@TempDir Path tmpDir) throws Exception {
        String path = tmpDir.resolve("cad_nocol.udbx").toString();
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            ds.createCadDataset("CadNoCol");
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
             var stmt = conn.prepareStatement(
                     "SELECT COUNT(*) AS cnt FROM geometry_columns WHERE f_table_name = 'cadnocol'");
             var rs = stmt.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("cnt")).isEqualTo(0);
        }
    }
}
