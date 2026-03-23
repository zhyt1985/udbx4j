package com.supermap.udbx.integration;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.TabularDataset;
import com.supermap.udbx.dataset.TabularRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：验证从真实 SampleData.udbx 读取 TabularDataset 正确。
 *
 * <p>测试数据来源：SampleData.udbx 中的 TabularDT 数据集。
 * <pre>
 *   TabularDT: DatasetType=0 (Tabular), ObjectCount=15, SRID=0
 * </pre>
 *
 * <p>对应白皮书 §3.1.1（纯属性表数据集），无几何字段。
 */
@Tag("integration")
class TabularDatasetReadTest {

    private static final String SAMPLE_DB = "src/test/resources/SampleData.udbx";

    @Test
    void should_get_tabular_dataset_by_name() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DB)) {
            TabularDataset dataset = (TabularDataset) ds.getDataset("TabularDT");

            assertThat(dataset).isNotNull();
            assertThat(dataset.getInfo().datasetName()).isEqualTo("TabularDT");
        }
    }

    @Test
    void should_read_all_records_from_tabular_dataset() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DB)) {
            TabularDataset dataset = (TabularDataset) ds.getDataset("TabularDT");

            List<TabularRecord> records = dataset.getRecords();

            // TabularDT: ObjectCount=15
            assertThat(records).hasSize(15);
        }
    }

    @Test
    void should_have_no_geometry_column() throws Exception {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DB)) {
            TabularDataset dataset = (TabularDataset) ds.getDataset("TabularDT");

            List<TabularRecord> records = dataset.getRecords();

            // 每条记录有 smId 和属性，但无几何字段
            assertThat(records).isNotEmpty();
            TabularRecord firstRecord = records.get(0);
            assertThat(firstRecord.smId()).isGreaterThan(0);
            // 属性中不含 SmGeometry 字段
            assertThat(firstRecord.attributes()).doesNotContainKey("SmGeometry");
        }
    }
}
