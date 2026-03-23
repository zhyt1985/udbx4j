package com.supermap.udbx.integration;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.core.DatasetType;
import com.supermap.udbx.dataset.Dataset;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.dataset.PointFeature;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 集成测试：验证从 SampleData.udbx 中读取点数据集（BaseMap_P）。
 *
 * <p>已知数据（来自 SmRegisterDaoSpecTest 验证）：
 * <pre>
 *   datasetId   = 1
 *   datasetName = "BaseMap_P"
 *   datasetType = Point (1)
 *   objectCount = 15
 *   srid        = 4326
 * </pre>
 */
@Tag("integration")
class PointDatasetReadTest {

    private static final String SAMPLE_DATA = "src/test/resources/SampleData.udbx";

    /**
     * 验证能够成功打开 SampleData.udbx 文件。
     */
    @Test
    void should_open_data_source_from_file() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DATA)) {
            assertThat(ds).isNotNull();
        }
    }

    /**
     * 验证能根据名称获取 BaseMap_P 点数据集，类型和对象数正确。
     */
    @Test
    void should_get_point_dataset_by_name() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DATA)) {
            Dataset dataset = ds.getDataset("BaseMap_P");

            assertThat(dataset).isNotNull();
            assertThat(dataset).isInstanceOf(PointDataset.class);
            assertThat(dataset.getName()).isEqualTo("BaseMap_P");
            assertThat(dataset.getType()).isEqualTo(DatasetType.Point);
            assertThat(dataset.getObjectCount()).isEqualTo(15);
            assertThat(dataset.getSrid()).isEqualTo(4326);
        }
    }

    /**
     * 验证能读取 BaseMap_P 中所有 15 个要素。
     */
    @Test
    void should_read_all_features_from_point_dataset() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DATA)) {
            PointDataset dataset = (PointDataset) ds.getDataset("BaseMap_P");

            List<PointFeature> features = dataset.getFeatures();

            assertThat(features).hasSize(15);
        }
    }

    /**
     * 验证第一个点的几何坐标和 SmID。
     *
     * <p>坐标为 WGS84（SRID=4326），经纬度范围合理。
     */
    @Test
    void should_read_first_feature_with_correct_geometry() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DATA)) {
            PointDataset dataset = (PointDataset) ds.getDataset("BaseMap_P");

            PointFeature feature = dataset.getFeature(1);

            assertThat(feature).isNotNull();
            assertThat(feature.smId()).isEqualTo(1);

            Point geom = feature.geometry();
            assertThat(geom).isNotNull();
            // WGS84 坐标：经度 -180~180，纬度 -90~90
            assertThat(geom.getX()).isBetween(-180.0, 180.0);
            assertThat(geom.getY()).isBetween(-90.0, 90.0);
        }
    }

    /**
     * 验证第一个要素的属性值（NAME 字段）。
     */
    @Test
    void should_read_first_feature_with_correct_attributes() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DATA)) {
            PointDataset dataset = (PointDataset) ds.getDataset("BaseMap_P");

            PointFeature feature = dataset.getFeature(1);

            assertThat(feature).isNotNull();
            assertThat(feature.attributes()).containsKey("NAME");
            // NAME 字段非 null（具体值取决于样本数据，只验证存在性和类型）
            Object nameValue = feature.attributes().get("NAME");
            assertThat(nameValue).isNotNull();
            assertThat(nameValue).isInstanceOf(String.class);
        }
    }
}
