package com.supermap.udbx.spec;

import com.supermap.udbx.core.DatasetKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 测试：验证 DatasetKind 枚举值与白皮书的一致性。
 *
 * <p>对应白皮书章节：表 1（数据集类型）
 *
 * <p>枚举值定义：
 * <pre>
 * SmDatasetKind 字段值 → Java 枚举常量
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
class DatasetKindSpecTest {

    @Test
    void tabular_type_value_must_be_0() {
        assertThat(DatasetKind.TABULAR.getValue()).isEqualTo(0);
    }

    @Test
    void point_type_value_must_be_1() {
        assertThat(DatasetKind.POINT.getValue()).isEqualTo(1);
    }

    @Test
    void line_type_value_must_be_3() {
        assertThat(DatasetKind.LINE.getValue()).isEqualTo(3);
    }

    @Test
    void network_type_value_must_be_4() {
        assertThat(DatasetKind.NETWORK.getValue()).isEqualTo(4);
    }

    @Test
    void region_type_value_must_be_5() {
        assertThat(DatasetKind.REGION.getValue()).isEqualTo(5);
    }

    @Test
    void text_type_value_must_be_7() {
        assertThat(DatasetKind.TEXT.getValue()).isEqualTo(7);
    }

    @Test
    void grid_type_value_must_be_83() {
        assertThat(DatasetKind.GRID.getValue()).isEqualTo(83);
    }

    @Test
    void image_type_value_must_be_88() {
        assertThat(DatasetKind.IMAGE.getValue()).isEqualTo(88);
    }

    @Test
    void voxel_grid_type_value_must_be_89() {
        assertThat(DatasetKind.VOXEL_GRID.getValue()).isEqualTo(89);
    }

    @Test
    void point_z_type_value_must_be_101() {
        assertThat(DatasetKind.POINT_Z.getValue()).isEqualTo(101);
    }

    @Test
    void line_z_type_value_must_be_103() {
        assertThat(DatasetKind.LINE_Z.getValue()).isEqualTo(103);
    }

    @Test
    void region_z_type_value_must_be_105() {
        assertThat(DatasetKind.REGION_Z.getValue()).isEqualTo(105);
    }

    @Test
    void cad_type_value_must_be_149() {
        assertThat(DatasetKind.CAD.getValue()).isEqualTo(149);
    }

    @Test
    void model_type_value_must_be_203() {
        assertThat(DatasetKind.MODEL.getValue()).isEqualTo(203);
    }

    @Test
    void network3d_type_value_must_be_205() {
        assertThat(DatasetKind.NETWORK_3D.getValue()).isEqualTo(205);
    }

    @Test
    void mosaic_type_value_must_be_206() {
        assertThat(DatasetKind.MOSAIC.getValue()).isEqualTo(206);
    }

    @Test
    void from_value_must_return_correct_enum_for_known_values() {
        assertThat(DatasetKind.fromValue(0)).isEqualTo(DatasetKind.TABULAR);
        assertThat(DatasetKind.fromValue(1)).isEqualTo(DatasetKind.POINT);
        assertThat(DatasetKind.fromValue(3)).isEqualTo(DatasetKind.LINE);
        assertThat(DatasetKind.fromValue(5)).isEqualTo(DatasetKind.REGION);
        assertThat(DatasetKind.fromValue(149)).isEqualTo(DatasetKind.CAD);
        assertThat(DatasetKind.fromValue(101)).isEqualTo(DatasetKind.POINT_Z);
        assertThat(DatasetKind.fromValue(103)).isEqualTo(DatasetKind.LINE_Z);
        assertThat(DatasetKind.fromValue(105)).isEqualTo(DatasetKind.REGION_Z);
    }

    @Test
    void from_value_must_throw_for_unknown_value() {
        assertThatThrownBy(() -> DatasetKind.fromValue(999))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("999");
    }
}
