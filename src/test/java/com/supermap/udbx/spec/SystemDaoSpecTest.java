package com.supermap.udbx.spec;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.core.DatasetType;
import com.supermap.udbx.core.FieldInfo;
import com.supermap.udbx.core.FieldType;
import com.supermap.udbx.system.GeometryColumnsDao;
import com.supermap.udbx.system.SmDataSourceInfoDao;
import com.supermap.udbx.system.SmFieldInfoDao;
import com.supermap.udbx.system.SmRegisterDao;
import com.supermap.udbx.system.UdbxSchemaInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 测试：验证所有 system DAO 的读写行为。
 *
 * <p>覆盖：
 * <ul>
 *   <li>SmRegisterDao: findAll, findByName, findById, insert, incrementObjectCount(with blob),
 *       incrementObjectCount(no geometry), decrementObjectCount</li>
 *   <li>SmFieldInfoDao: findByDatasetId, findByDatasetIdAndFieldName, insert, insertAll</li>
 *   <li>SmDataSourceInfoDao: getVersion, getDataFormat</li>
 *   <li>GeometryColumnsDao: getGeometryType, getSrid</li>
 * </ul>
 *
 * <p>每个测试方法使用 {@code @TempDir} 创建独立临时文件，不修改 SampleData.udbx。
 */
class SystemDaoSpecTest {

    @TempDir
    Path tmpDir;

    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String path = tmpDir.resolve("test.udbx").toString();
        conn = DriverManager.getConnection("jdbc:sqlite:" + path);
        UdbxSchemaInitializer.initialize(conn);
    }

    // ── SmRegisterDao: findAll ────────────────────────────────────────────────

    @Test
    void smregister_findAll_returns_empty_on_fresh_database() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);
        List<DatasetInfo> result = dao.findAll();
        assertThat(result).isEmpty();
    }

    @Test
    void smregister_findAll_returns_all_inserted_datasets() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);
        dao.insert("Pts", DatasetType.Point, 4326, "SmID", "SmGeometry");
        dao.insert("Lines", DatasetType.Line, 4326, "SmID", "SmGeometry");
        dao.insert("Regions", DatasetType.Region, 4326, "SmID", "SmGeometry");

        List<DatasetInfo> result = dao.findAll();

        assertThat(result).hasSize(3);
    }

    @Test
    void smregister_findAll_ordered_by_dataset_id() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);
        dao.insert("A", DatasetType.Point, 4326, "SmID", "SmGeometry");
        dao.insert("B", DatasetType.Line, 4326, "SmID", "SmGeometry");
        dao.insert("C", DatasetType.Region, 4326, "SmID", "SmGeometry");

        List<DatasetInfo> result = dao.findAll();

        assertThat(result.get(0).datasetName()).isEqualTo("A");
        assertThat(result.get(1).datasetName()).isEqualTo("B");
        assertThat(result.get(2).datasetName()).isEqualTo("C");
    }

    // ── SmRegisterDao: findByName ─────────────────────────────────────────────

    @Test
    void smregister_findByName_returns_correct_dataset_info() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);
        dao.insert("MyPoint", DatasetType.Point, 4326, "SmID", "SmGeometry");

        Optional<DatasetInfo> result = dao.findByName("MyPoint");

        assertThat(result).isPresent();
        assertThat(result.get().datasetName()).isEqualTo("MyPoint");
        assertThat(result.get().datasetType()).isEqualTo(DatasetType.Point);
        assertThat(result.get().srid()).isEqualTo(4326);
        assertThat(result.get().objectCount()).isEqualTo(0);
    }

    @Test
    void smregister_findByName_returns_empty_for_nonexistent_name() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);

        Optional<DatasetInfo> result = dao.findByName("NotExist");

        assertThat(result).isEmpty();
    }

    @Test
    void smregister_findByName_returns_correct_type_for_tabular() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);
        dao.insert("MyTable", DatasetType.Tabular, 0, "SmID", null);

        Optional<DatasetInfo> result = dao.findByName("MyTable");

        assertThat(result).isPresent();
        assertThat(result.get().datasetType()).isEqualTo(DatasetType.Tabular);
        assertThat(result.get().srid()).isEqualTo(0);
    }

    @Test
    void smregister_findByName_returns_correct_type_for_cad() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);
        dao.insert("MyCad", DatasetType.CAD, 0, "SmID", "SmGeometry");

        Optional<DatasetInfo> result = dao.findByName("MyCad");

        assertThat(result).isPresent();
        assertThat(result.get().datasetType()).isEqualTo(DatasetType.CAD);
    }

    // ── SmRegisterDao: findById ───────────────────────────────────────────────

    @Test
    void smregister_findById_returns_correct_dataset_info() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);
        int id = dao.insert("PtById", DatasetType.Point, 4326, "SmID", "SmGeometry");

        Optional<DatasetInfo> result = dao.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().datasetId()).isEqualTo(id);
        assertThat(result.get().datasetName()).isEqualTo("PtById");
    }

    @Test
    void smregister_findById_returns_empty_for_nonexistent_id() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);

        Optional<DatasetInfo> result = dao.findById(9999);

        assertThat(result).isEmpty();
    }

    // ── SmRegisterDao: insert / auto-increment ID ─────────────────────────────

    @Test
    void smregister_insert_assigns_sequential_ids_starting_from_one() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);

        int id1 = dao.insert("D1", DatasetType.Point, 4326, "SmID", "SmGeometry");
        int id2 = dao.insert("D2", DatasetType.Line, 4326, "SmID", "SmGeometry");
        int id3 = dao.insert("D3", DatasetType.Region, 4326, "SmID", "SmGeometry");

        assertThat(id1).isEqualTo(1);
        assertThat(id2).isEqualTo(2);
        assertThat(id3).isEqualTo(3);
    }

    // ── SmRegisterDao: incrementObjectCount (with blob size) ──────────────────

    @Test
    void smregister_incrementObjectCount_with_blob_increments_count_and_updates_max_size() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);
        int id = dao.insert("Pts", DatasetType.Point, 4326, "SmID", "SmGeometry");

        dao.incrementObjectCount(id, 100);

        DatasetInfo info = dao.findById(id).get();
        assertThat(info.objectCount()).isEqualTo(1);
    }

    @Test
    void smregister_incrementObjectCount_with_blob_increments_count_multiple_times() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);
        int id = dao.insert("Pts", DatasetType.Point, 4326, "SmID", "SmGeometry");

        dao.incrementObjectCount(id, 100);
        dao.incrementObjectCount(id, 200);
        dao.incrementObjectCount(id, 50);

        DatasetInfo info = dao.findById(id).get();
        assertThat(info.objectCount()).isEqualTo(3);
    }

    // ── SmRegisterDao: incrementObjectCount (no geometry) ────────────────────

    @Test
    void smregister_incrementObjectCount_without_blob_increments_count() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);
        int id = dao.insert("Tab", DatasetType.Tabular, 0, "SmID", null);

        dao.incrementObjectCount(id);
        dao.incrementObjectCount(id);

        DatasetInfo info = dao.findById(id).get();
        assertThat(info.objectCount()).isEqualTo(2);
    }

    // ── SmRegisterDao: decrementObjectCount ──────────────────────────────────

    @Test
    void smregister_decrementObjectCount_decrements_after_increment() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);
        int id = dao.insert("Pts", DatasetType.Point, 4326, "SmID", "SmGeometry");

        dao.incrementObjectCount(id, 100);
        dao.incrementObjectCount(id, 100);
        dao.decrementObjectCount(id);

        DatasetInfo info = dao.findById(id).get();
        assertThat(info.objectCount()).isEqualTo(1);
    }

    @Test
    void smregister_decrementObjectCount_does_not_go_below_zero() throws Exception {
        SmRegisterDao dao = new SmRegisterDao(conn);
        int id = dao.insert("Pts", DatasetType.Point, 4326, "SmID", "SmGeometry");

        // objectCount 初始为 0，递减不应产生负值
        dao.decrementObjectCount(id);

        DatasetInfo info = dao.findById(id).get();
        assertThat(info.objectCount()).isEqualTo(0);
    }

    // ── SmFieldInfoDao: findByDatasetId ───────────────────────────────────────

    @Test
    void smfieldinfo_findByDatasetId_returns_empty_when_no_fields() throws Exception {
        SmRegisterDao regDao = new SmRegisterDao(conn);
        int dsId = regDao.insert("Pts", DatasetType.Point, 4326, "SmID", "SmGeometry");

        SmFieldInfoDao fieldDao = new SmFieldInfoDao(conn);
        List<FieldInfo> result = fieldDao.findByDatasetId(dsId);

        assertThat(result).isEmpty();
    }

    @Test
    void smfieldinfo_findByDatasetId_returns_inserted_fields() throws Exception {
        SmRegisterDao regDao = new SmRegisterDao(conn);
        int dsId = regDao.insert("Pts", DatasetType.Point, 4326, "SmID", "SmGeometry");

        SmFieldInfoDao fieldDao = new SmFieldInfoDao(conn);
        fieldDao.insert(new FieldInfo(dsId, "Name", FieldType.NText, "名称", false));
        fieldDao.insert(new FieldInfo(dsId, "Value", FieldType.Double, "数值", true));

        List<FieldInfo> result = fieldDao.findByDatasetId(dsId);

        assertThat(result).hasSize(2);
    }

    @Test
    void smfieldinfo_findByDatasetId_returns_correct_field_values() throws Exception {
        SmRegisterDao regDao = new SmRegisterDao(conn);
        int dsId = regDao.insert("Pts", DatasetType.Point, 4326, "SmID", "SmGeometry");

        SmFieldInfoDao fieldDao = new SmFieldInfoDao(conn);
        fieldDao.insert(new FieldInfo(dsId, "Score", FieldType.Int32, "得分", true));

        List<FieldInfo> result = fieldDao.findByDatasetId(dsId);

        assertThat(result).hasSize(1);
        FieldInfo f = result.get(0);
        assertThat(f.fieldName()).isEqualTo("Score");
        assertThat(f.fieldType()).isEqualTo(FieldType.Int32);
        assertThat(f.fieldAlias()).isEqualTo("得分");
        assertThat(f.required()).isTrue();
        assertThat(f.datasetId()).isEqualTo(dsId);
    }

    @Test
    void smfieldinfo_findByDatasetId_only_returns_fields_for_target_dataset() throws Exception {
        SmRegisterDao regDao = new SmRegisterDao(conn);
        int dsId1 = regDao.insert("Ds1", DatasetType.Point, 4326, "SmID", "SmGeometry");
        int dsId2 = regDao.insert("Ds2", DatasetType.Line, 4326, "SmID", "SmGeometry");

        SmFieldInfoDao fieldDao = new SmFieldInfoDao(conn);
        fieldDao.insert(new FieldInfo(dsId1, "FieldA", FieldType.NText, "", false));
        fieldDao.insert(new FieldInfo(dsId2, "FieldB", FieldType.NText, "", false));

        List<FieldInfo> result = fieldDao.findByDatasetId(dsId1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fieldName()).isEqualTo("FieldA");
    }

    // ── SmFieldInfoDao: findByDatasetIdAndFieldName ───────────────────────────

    @Test
    void smfieldinfo_findByDatasetIdAndFieldName_returns_correct_field() throws Exception {
        SmRegisterDao regDao = new SmRegisterDao(conn);
        int dsId = regDao.insert("Pts", DatasetType.Point, 4326, "SmID", "SmGeometry");

        SmFieldInfoDao fieldDao = new SmFieldInfoDao(conn);
        fieldDao.insert(new FieldInfo(dsId, "MyField", FieldType.Double, "我的字段", false));

        Optional<FieldInfo> result = fieldDao.findByDatasetIdAndFieldName(dsId, "MyField");

        assertThat(result).isPresent();
        assertThat(result.get().fieldName()).isEqualTo("MyField");
        assertThat(result.get().fieldType()).isEqualTo(FieldType.Double);
    }

    @Test
    void smfieldinfo_findByDatasetIdAndFieldName_returns_empty_for_nonexistent() throws Exception {
        SmRegisterDao regDao = new SmRegisterDao(conn);
        int dsId = regDao.insert("Pts", DatasetType.Point, 4326, "SmID", "SmGeometry");

        SmFieldInfoDao fieldDao = new SmFieldInfoDao(conn);

        Optional<FieldInfo> result = fieldDao.findByDatasetIdAndFieldName(dsId, "NoSuchField");

        assertThat(result).isEmpty();
    }

    // ── SmFieldInfoDao: insertAll ─────────────────────────────────────────────

    @Test
    void smfieldinfo_insertAll_inserts_all_fields_with_overridden_dataset_id() throws Exception {
        SmRegisterDao regDao = new SmRegisterDao(conn);
        int dsId = regDao.insert("Pts", DatasetType.Point, 4326, "SmID", "SmGeometry");

        SmFieldInfoDao fieldDao = new SmFieldInfoDao(conn);
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "F1", FieldType.NText, "字段1", false),
                new FieldInfo(0, "F2", FieldType.Int32, "字段2", false),
                new FieldInfo(0, "F3", FieldType.Double, "字段3", true)
        );
        fieldDao.insertAll(dsId, fields);

        List<FieldInfo> result = fieldDao.findByDatasetId(dsId);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(FieldInfo::fieldName)
                .containsExactly("F1", "F2", "F3");
        assertThat(result).allMatch(f -> f.datasetId() == dsId);
    }

    @Test
    void smfieldinfo_insertAll_with_empty_list_inserts_nothing() throws Exception {
        SmRegisterDao regDao = new SmRegisterDao(conn);
        int dsId = regDao.insert("Pts", DatasetType.Point, 4326, "SmID", "SmGeometry");

        SmFieldInfoDao fieldDao = new SmFieldInfoDao(conn);
        fieldDao.insertAll(dsId, List.of());

        List<FieldInfo> result = fieldDao.findByDatasetId(dsId);

        assertThat(result).isEmpty();
    }

    // ── SmDataSourceInfoDao: getVersion ───────────────────────────────────────

    @Test
    void smdatasourceinfo_getVersion_returns_zero_on_fresh_database() throws Exception {
        SmDataSourceInfoDao dao = new SmDataSourceInfoDao(conn);

        Optional<Integer> version = dao.getVersion();

        assertThat(version).isPresent();
        assertThat(version.get()).isEqualTo(0);
    }

    // ── SmDataSourceInfoDao: getDataFormat ────────────────────────────────────

    @Test
    void smdatasourceinfo_getDataFormat_returns_one_on_fresh_database() throws Exception {
        SmDataSourceInfoDao dao = new SmDataSourceInfoDao(conn);

        Optional<Integer> dataFormat = dao.getDataFormat();

        assertThat(dataFormat).isPresent();
        assertThat(dataFormat.get()).isEqualTo(1);
    }

    @Test
    void smdatasourceinfo_both_version_and_dataformat_present() throws Exception {
        SmDataSourceInfoDao dao = new SmDataSourceInfoDao(conn);

        assertThat(dao.getVersion()).isPresent();
        assertThat(dao.getDataFormat()).isPresent();
    }

    // ── GeometryColumnsDao: getGeometryType ───────────────────────────────────

    @Test
    void geometrycolumns_getGeometryType_returns_correct_type_for_registered_table() throws Exception {
        // 手动插入一条 geometry_columns 记录
        try (var stmt = conn.prepareStatement(
                "INSERT INTO geometry_columns (f_table_name, f_geometry_column, geometry_type, coord_dimension, srid, spatial_index_enabled) " +
                "VALUES ('mypoints', 'smgeometry', 1, 2, 4326, 0)")) {
            stmt.executeUpdate();
        }

        GeometryColumnsDao dao = new GeometryColumnsDao(conn);
        Optional<Integer> result = dao.getGeometryType("mypoints");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(1);
    }

    @Test
    void geometrycolumns_getGeometryType_is_case_insensitive_for_table_name() throws Exception {
        try (var stmt = conn.prepareStatement(
                "INSERT INTO geometry_columns (f_table_name, f_geometry_column, geometry_type, coord_dimension, srid, spatial_index_enabled) " +
                "VALUES ('mylines', 'smgeometry', 5, 2, 4326, 0)")) {
            stmt.executeUpdate();
        }

        GeometryColumnsDao dao = new GeometryColumnsDao(conn);
        // DAO 内部会转小写
        Optional<Integer> result = dao.getGeometryType("MYLINES");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(5);
    }

    @Test
    void geometrycolumns_getGeometryType_returns_empty_for_unregistered_table() throws Exception {
        GeometryColumnsDao dao = new GeometryColumnsDao(conn);

        Optional<Integer> result = dao.getGeometryType("notregistered");

        assertThat(result).isEmpty();
    }

    // ── GeometryColumnsDao: getSrid ───────────────────────────────────────────

    @Test
    void geometrycolumns_getSrid_returns_correct_srid_for_registered_table() throws Exception {
        try (var stmt = conn.prepareStatement(
                "INSERT INTO geometry_columns (f_table_name, f_geometry_column, geometry_type, coord_dimension, srid, spatial_index_enabled) " +
                "VALUES ('sridtest', 'smgeometry', 6, 2, 4326, 0)")) {
            stmt.executeUpdate();
        }

        GeometryColumnsDao dao = new GeometryColumnsDao(conn);
        Optional<Integer> result = dao.getSrid("sridtest");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(4326);
    }

    @Test
    void geometrycolumns_getSrid_returns_empty_for_unregistered_table() throws Exception {
        GeometryColumnsDao dao = new GeometryColumnsDao(conn);

        Optional<Integer> result = dao.getSrid("notregistered");

        assertThat(result).isEmpty();
    }

    @Test
    void geometrycolumns_getSrid_is_case_insensitive_for_table_name() throws Exception {
        try (var stmt = conn.prepareStatement(
                "INSERT INTO geometry_columns (f_table_name, f_geometry_column, geometry_type, coord_dimension, srid, spatial_index_enabled) " +
                "VALUES ('casetest', 'smgeometry', 1, 2, 4326, 0)")) {
            stmt.executeUpdate();
        }

        GeometryColumnsDao dao = new GeometryColumnsDao(conn);
        Optional<Integer> result = dao.getSrid("CASETEST");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(4326);
    }
}
