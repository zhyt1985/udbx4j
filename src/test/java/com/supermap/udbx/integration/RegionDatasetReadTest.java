package com.supermap.udbx.integration;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.RegionDataset;
import com.supermap.udbx.dataset.RegionFeature;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.MultiPolygon;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：验证从真实 SampleData.udbx 读取 RegionDataset 正确。
 *
 * <p>测试数据来源：SampleData.udbx 中的 BaseMap_R 数据集。
 * <pre>
 *   BaseMap_R: DatasetType=5 (Region), ObjectCount=15, SRID=4326
 * </pre>
 *
 * <p>对应白皮书 §3.1.4（面数据集）和 §4.2.5（GAIA MultiPolygon 格式）。
 */
@Tag("integration")
class RegionDatasetReadTest {

    private static final String SAMPLE_DB = "src/test/resources/SampleData.udbx";

    @Test
    void should_get_region_dataset_by_name() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DB)) {
            RegionDataset dataset = (RegionDataset) ds.getDataset("BaseMap_R");

            assertThat(dataset).isNotNull();
            assertThat(dataset.getInfo().datasetName()).isEqualTo("BaseMap_R");
        }
    }

    @Test
    void should_read_all_features_from_region_dataset() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DB)) {
            RegionDataset dataset = (RegionDataset) ds.getDataset("BaseMap_R");

            List<RegionFeature> features = dataset.getFeatures();

            // BaseMap_R: ObjectCount=15
            assertThat(features).hasSize(15);
        }
    }

    @Test
    void should_read_feature_with_multipolygon_geometry() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DB)) {
            RegionDataset dataset = (RegionDataset) ds.getDataset("BaseMap_R");

            List<RegionFeature> features = dataset.getFeatures();
            RegionFeature firstFeature = features.get(0);

            assertThat(firstFeature.smId()).isGreaterThan(0);
            assertThat(firstFeature.geometry()).isNotNull();
            assertThat(firstFeature.geometry()).isInstanceOf(MultiPolygon.class);
            assertThat(firstFeature.geometry().isEmpty()).isFalse();
        }
    }
}
