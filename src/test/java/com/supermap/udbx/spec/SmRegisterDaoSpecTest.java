package com.supermap.udbx.spec;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.core.DatasetType;
import com.supermap.udbx.system.SmRegisterDao;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 测试：验证 SmRegisterDao 能够正确读取数据集元信息。
 *
 * <p>对应白皮书 §2.1 表 5 SmRegister 字段定义。
 *
 * <p>测试数据（SampleData.udbx 中的已知记录）：
 * <pre>
 *   BaseMap_P:  DatasetType=1 (Point),  ObjectCount=15, SRID=4326
 *   BaseMap_L:  DatasetType=3 (Line),   ObjectCount=47, SRID=4326
 *   BaseMap_R:  DatasetType=5 (Region), ObjectCount=15, SRID=4326
 *   CADDT:      DatasetType=149 (CAD),  ObjectCount=92, SRID=0
 * </pre>
 */
class SmRegisterDaoSpecTest {

    private static final Path SAMPLE_DATA = Paths.get("src/test/resources/SampleData.udbx");

    @Test
    void should_find_all_datasets_from_sample_data() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmRegisterDao dao = new SmRegisterDao(conn);

            List<DatasetInfo> datasets = dao.findAll();

            // SampleData.udbx 至少包含 10 个数据集
            assertThat(datasets).hasSizeGreaterThanOrEqualTo(10);
        }
    }

    @Test
    void should_find_basemap_p_with_correct_metadata() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmRegisterDao dao = new SmRegisterDao(conn);

            Optional<DatasetInfo> result = dao.findByName("BaseMap_P");

            assertThat(result).isPresent();
            DatasetInfo info = result.get();
            assertThat(info.datasetId()).isEqualTo(1);
            assertThat(info.datasetName()).isEqualTo("BaseMap_P");
            assertThat(info.datasetType()).isEqualTo(DatasetType.Point);
            assertThat(info.objectCount()).isEqualTo(15);
            assertThat(info.srid()).isEqualTo(4326);
        }
    }

    @Test
    void should_find_basemap_l_with_correct_metadata() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmRegisterDao dao = new SmRegisterDao(conn);

            Optional<DatasetInfo> result = dao.findByName("BaseMap_L");

            assertThat(result).isPresent();
            DatasetInfo info = result.get();
            assertThat(info.datasetId()).isEqualTo(2);
            assertThat(info.datasetName()).isEqualTo("BaseMap_L");
            assertThat(info.datasetType()).isEqualTo(DatasetType.Line);
            assertThat(info.objectCount()).isEqualTo(47);
            assertThat(info.srid()).isEqualTo(4326);
        }
    }

    @Test
    void should_find_basemap_r_with_correct_metadata() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmRegisterDao dao = new SmRegisterDao(conn);

            Optional<DatasetInfo> result = dao.findByName("BaseMap_R");

            assertThat(result).isPresent();
            DatasetInfo info = result.get();
            assertThat(info.datasetId()).isEqualTo(3);
            assertThat(info.datasetName()).isEqualTo("BaseMap_R");
            assertThat(info.datasetType()).isEqualTo(DatasetType.Region);
            assertThat(info.objectCount()).isEqualTo(15);
            assertThat(info.srid()).isEqualTo(4326);
        }
    }

    @Test
    void should_find_cad_dataset_with_correct_metadata() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmRegisterDao dao = new SmRegisterDao(conn);

            Optional<DatasetInfo> result = dao.findByName("CADDT");

            assertThat(result).isPresent();
            DatasetInfo info = result.get();
            assertThat(info.datasetId()).isEqualTo(9);
            assertThat(info.datasetName()).isEqualTo("CADDT");
            assertThat(info.datasetType()).isEqualTo(DatasetType.CAD);
            assertThat(info.objectCount()).isEqualTo(92);
            assertThat(info.srid()).isEqualTo(0);
        }
    }

    @Test
    void should_find_by_id() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmRegisterDao dao = new SmRegisterDao(conn);

            Optional<DatasetInfo> result = dao.findById(1);

            assertThat(result).isPresent();
            assertThat(result.get().datasetName()).isEqualTo("BaseMap_P");
        }
    }

    @Test
    void should_return_empty_for_nonexistent_name() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmRegisterDao dao = new SmRegisterDao(conn);

            Optional<DatasetInfo> result = dao.findByName("NonExistent");

            assertThat(result).isEmpty();
        }
    }

    @Test
    void should_return_empty_for_nonexistent_id() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmRegisterDao dao = new SmRegisterDao(conn);

            Optional<DatasetInfo> result = dao.findById(9999);

            assertThat(result).isEmpty();
        }
    }

    @Test
    void should_find_tabular_dataset_with_zero_srid() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmRegisterDao dao = new SmRegisterDao(conn);

            Optional<DatasetInfo> result = dao.findByName("TabularDT");

            assertThat(result).isPresent();
            DatasetInfo info = result.get();
            assertThat(info.datasetType()).isEqualTo(DatasetType.Tabular);
            assertThat(info.srid()).isEqualTo(0);
        }
    }

    @Test
    void should_find_pointz_dataset_with_correct_type() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmRegisterDao dao = new SmRegisterDao(conn);

            Optional<DatasetInfo> result = dao.findByName("BaseMap_PZ");

            assertThat(result).isPresent();
            DatasetInfo info = result.get();
            assertThat(info.datasetType()).isEqualTo(DatasetType.PointZ);
            assertThat(info.objectCount()).isEqualTo(15);
        }
    }
}
