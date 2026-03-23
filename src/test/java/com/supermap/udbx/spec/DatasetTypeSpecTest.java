package com.supermap.udbx.spec;

import com.supermap.udbx.core.DatasetType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 测试：验证 DatasetType 枚举值与白皮书的一致性。
 *
 * <p>对应白皮书章节：表 1（数据集类型）
 *
 * <p>枚举值定义：
 * <pre>
 * SmDatasetType 字段值 → Java 枚举常量
 *   0   → Tabular
 *   1   → Point
 *   3   → Line
 *   4   → Network
 *   5   → Region
 *   7   → Text
 *   83  → Grid
 *   88  → Image
 *   89  → VoxelGrid
 *   101 → PointZ
 *   103 → LineZ
 *   105 → RegionZ
 *   149 → CAD
 *   203 → Model
 *   205 → Network3D
 *   206 → Mosaic
 * </pre>
 */
class DatasetTypeSpecTest {

    @Test
    void tabular_type_value_must_be_0() {
        assertThat(DatasetType.Tabular.getValue()).isEqualTo(0);
    }

    @Test
    void point_type_value_must_be_1() {
        assertThat(DatasetType.Point.getValue()).isEqualTo(1);
    }

    @Test
    void line_type_value_must_be_3() {
        assertThat(DatasetType.Line.getValue()).isEqualTo(3);
    }

    @Test
    void network_type_value_must_be_4() {
        assertThat(DatasetType.Network.getValue()).isEqualTo(4);
    }

    @Test
    void region_type_value_must_be_5() {
        assertThat(DatasetType.Region.getValue()).isEqualTo(5);
    }

    @Test
    void text_type_value_must_be_7() {
        assertThat(DatasetType.Text.getValue()).isEqualTo(7);
    }

    @Test
    void grid_type_value_must_be_83() {
        assertThat(DatasetType.Grid.getValue()).isEqualTo(83);
    }

    @Test
    void image_type_value_must_be_88() {
        assertThat(DatasetType.Image.getValue()).isEqualTo(88);
    }

    @Test
    void voxel_grid_type_value_must_be_89() {
        assertThat(DatasetType.VoxelGrid.getValue()).isEqualTo(89);
    }

    @Test
    void point_z_type_value_must_be_101() {
        assertThat(DatasetType.PointZ.getValue()).isEqualTo(101);
    }

    @Test
    void line_z_type_value_must_be_103() {
        assertThat(DatasetType.LineZ.getValue()).isEqualTo(103);
    }

    @Test
    void region_z_type_value_must_be_105() {
        assertThat(DatasetType.RegionZ.getValue()).isEqualTo(105);
    }

    @Test
    void cad_type_value_must_be_149() {
        assertThat(DatasetType.CAD.getValue()).isEqualTo(149);
    }

    @Test
    void model_type_value_must_be_203() {
        assertThat(DatasetType.Model.getValue()).isEqualTo(203);
    }

    @Test
    void network3d_type_value_must_be_205() {
        assertThat(DatasetType.Network3D.getValue()).isEqualTo(205);
    }

    @Test
    void mosaic_type_value_must_be_206() {
        assertThat(DatasetType.Mosaic.getValue()).isEqualTo(206);
    }

    @Test
    void from_value_must_return_correct_enum_for_known_values() {
        assertThat(DatasetType.fromValue(0)).isEqualTo(DatasetType.Tabular);
        assertThat(DatasetType.fromValue(1)).isEqualTo(DatasetType.Point);
        assertThat(DatasetType.fromValue(3)).isEqualTo(DatasetType.Line);
        assertThat(DatasetType.fromValue(5)).isEqualTo(DatasetType.Region);
        assertThat(DatasetType.fromValue(149)).isEqualTo(DatasetType.CAD);
        assertThat(DatasetType.fromValue(101)).isEqualTo(DatasetType.PointZ);
        assertThat(DatasetType.fromValue(103)).isEqualTo(DatasetType.LineZ);
        assertThat(DatasetType.fromValue(105)).isEqualTo(DatasetType.RegionZ);
    }

    @Test
    void from_value_must_throw_for_unknown_value() {
        assertThatThrownBy(() -> DatasetType.fromValue(999))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("999");
    }
}
