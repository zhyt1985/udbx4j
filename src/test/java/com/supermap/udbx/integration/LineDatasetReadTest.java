package com.supermap.udbx.integration;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.LineDataset;
import com.supermap.udbx.dataset.LineFeature;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.MultiLineString;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：验证从真实 SampleData.udbx 读取 LineDataset 正确。
 *
 * <p>测试数据来源：SampleData.udbx 中的 BaseMap_L 数据集。
 * <pre>
 *   BaseMap_L: DatasetType=3 (Line), ObjectCount=47, SRID=4326
 * </pre>
 *
 * <p>对应白皮书 §3.1.3（线数据集）和 §4.2.3（GAIA MultiLineString 格式）。
 */
@Tag("integration")
class LineDatasetReadTest {

    private static final String SAMPLE_DB = "src/test/resources/SampleData.udbx";

    @Test
    void should_get_line_dataset_by_name() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DB)) {
            LineDataset dataset = (LineDataset) ds.getDataset("BaseMap_L");

            assertThat(dataset).isNotNull();
            assertThat(dataset.getInfo().datasetName()).isEqualTo("BaseMap_L");
        }
    }

    @Test
    void should_read_all_features_from_line_dataset() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DB)) {
            LineDataset dataset = (LineDataset) ds.getDataset("BaseMap_L");

            List<LineFeature> features = dataset.getFeatures();

            // BaseMap_L: ObjectCount=47
            assertThat(features).hasSize(47);
        }
    }

    @Test
    void should_read_feature_with_multilinestring_geometry() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DB)) {
            LineDataset dataset = (LineDataset) ds.getDataset("BaseMap_L");

            List<LineFeature> features = dataset.getFeatures();
            LineFeature firstFeature = features.get(0);

            assertThat(firstFeature.smId()).isGreaterThan(0);
            assertThat(firstFeature.geometry()).isNotNull();
            assertThat(firstFeature.geometry()).isInstanceOf(MultiLineString.class);
            assertThat(firstFeature.geometry().isEmpty()).isFalse();
        }
    }
}
