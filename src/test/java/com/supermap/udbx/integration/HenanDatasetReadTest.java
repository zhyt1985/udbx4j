package com.supermap.udbx.integration;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.core.DatasetType;
import com.supermap.udbx.dataset.LineDataset;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.dataset.RegionDataset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * henan.udbx 集成测试。
 *
 * <p>验证针对真实文件的两类修复：
 * <ol>
 *   <li>幽灵表容错：SmRegister 中注册但物理表不存在的数据集，{@code getFeatures()} 应返回空列表而非抛异常。</li>
 *   <li>Text 类型降级：{@code DatasetType.Text} 数据集，{@code getDataset()} 应返回 {@code null} 而非崩溃。</li>
 * </ol>
 */
@Tag("integration")
class HenanDatasetReadTest {

    private static final String FILE = "src/test/resources/henan.udbx";

    // ── 正常路径：有效数据集应成功读取 ─────────────────────────────────────────

    @Test
    void valid_point_dataset_returns_features() {
        try (UdbxDataSource ds = UdbxDataSource.open(FILE)) {
            PointDataset dataset = (PointDataset) ds.getDataset("居民地地名");
            assertThat(dataset).isNotNull();
            assertThat(dataset.getFeatures()).isNotEmpty();
        }
    }

    @Test
    void valid_line_dataset_returns_features() {
        try (UdbxDataSource ds = UdbxDataSource.open(FILE)) {
            LineDataset dataset = (LineDataset) ds.getDataset("公路");
            assertThat(dataset).isNotNull();
            assertThat(dataset.getFeatures()).isNotEmpty();
        }
    }

    @Test
    void valid_region_dataset_returns_features() {
        try (UdbxDataSource ds = UdbxDataSource.open(FILE)) {
            RegionDataset dataset = (RegionDataset) ds.getDataset("省级行政区划");
            assertThat(dataset).isNotNull();
            assertThat(dataset.getFeatures()).isNotEmpty();
        }
    }

    // ── 幽灵表：getFeatures() 应返回空列表，不抛异常 ──────────────────────────

    @Test
    void ghost_point_dataset_returns_empty_list() {
        try (UdbxDataSource ds = UdbxDataSource.open(FILE)) {
            // "streetroad" 在 SmRegister 中注册但无对应物理表
            PointDataset dataset = (PointDataset) ds.getDataset("streetroad");
            assertThat(dataset).isNotNull();
            assertThat(dataset.getFeatures())
                    .as("幽灵表数据集 getFeatures() 应返回空列表")
                    .isEmpty();
        }
    }

    @Test
    void ghost_line_dataset_returns_empty_list() {
        try (UdbxDataSource ds = UdbxDataSource.open(FILE)) {
            // "groad" 在 SmRegister 中注册但无对应物理表
            LineDataset dataset = (LineDataset) ds.getDataset("groad");
            assertThat(dataset).isNotNull();
            assertThat(dataset.getFeatures())
                    .as("幽灵表数据集 getFeatures() 应返回空列表")
                    .isEmpty();
        }
    }

    @Test
    void ghost_region_dataset_returns_empty_list() {
        try (UdbxDataSource ds = UdbxDataSource.open(FILE)) {
            // "city" 在 SmRegister 中注册但无对应物理表
            RegionDataset dataset = (RegionDataset) ds.getDataset("city");
            assertThat(dataset).isNotNull();
            assertThat(dataset.getFeatures())
                    .as("幽灵表数据集 getFeatures() 应返回空列表")
                    .isEmpty();
        }
    }

    // ── Text 类型：getDataset() 应返回 null（不支持，不崩溃）──────────────────

    @Test
    void text_dataset_getDataset_returns_null() {
        try (UdbxDataSource ds = UdbxDataSource.open(FILE)) {
            // DatasetType.Text 当前不支持，getDataset() 应返回 null 而非抛异常
            assertThat(ds.getDataset("河南省标签"))
                    .as("不支持的 Text 类型数据集应返回 null")
                    .isNull();
        }
    }

    // ── 辅助验证：listDatasetInfos() 能正确枚举所有数据集 ────────────────────

    @Test
    void list_dataset_infos_includes_all_types() {
        try (UdbxDataSource ds = UdbxDataSource.open(FILE)) {
            List<DatasetInfo> infos = ds.listDatasetInfos();
            assertThat(infos).isNotEmpty();
            // 应包含 Text 类型数据集
            assertThat(infos)
                    .anyMatch(i -> i.datasetType() == DatasetType.Text)
                    .anyMatch(i -> i.datasetType() == DatasetType.Point)
                    .anyMatch(i -> i.datasetType() == DatasetType.Line)
                    .anyMatch(i -> i.datasetType() == DatasetType.Region);
        }
    }
}
