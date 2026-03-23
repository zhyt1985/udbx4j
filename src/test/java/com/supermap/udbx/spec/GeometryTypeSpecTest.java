package com.supermap.udbx.spec;

import com.supermap.udbx.core.GeometryType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 测试：验证 GeometryType 枚举值与白皮书的一致性。
 *
 * <p>对应白皮书章节：表 2（Geometry 类型）+ §4.2、§4.3 中的类型常量
 *
 * <p>GAIA 格式 geoType 常量（§4.2）：
 * <pre>
 *   1    → GAIAPoint
 *   3    → GAIAPolygon（单多边形，用于内部）
 *   5    → GAIAMultiLineString
 *   6    → GAIAMultiPolygon
 *   1001 → GAIAPointZ
 *   1005 → GAIAMultiLineStringZ
 *   1006 → GAIAMultiPolygonZ
 * </pre>
 *
 * <p>GeoHeader 格式 geoType 常量（§4.3）：
 * <pre>
 *   1  → GeoPoint
 *   3  → GeoLine
 *   5  → GeoRegion
 *   12 → GeoRect
 *   15 → GeoCircle
 *   20 → GeoEllipse
 *   21 → GeoPie
 *   24 → GeoArc
 *   25 → GeoEllipticArc
 *   27 → GeoCardinal
 *   28 → GeoCurve
 *   29 → GeoBSpline
 * </pre>
 */
class GeometryTypeSpecTest {

    // ── GAIA 格式 ──────────────────────────────────────────────────────────

    @Test
    void gaia_point_type_value_must_be_1() {
        assertThat(GeometryType.GAIAPoint.getValue()).isEqualTo(1);
    }

    @Test
    void gaia_polygon_type_value_must_be_3() {
        assertThat(GeometryType.GAIAPolygon.getValue()).isEqualTo(3);
    }

    @Test
    void gaia_multi_line_string_type_value_must_be_5() {
        assertThat(GeometryType.GAIAMultiLineString.getValue()).isEqualTo(5);
    }

    @Test
    void gaia_multi_polygon_type_value_must_be_6() {
        assertThat(GeometryType.GAIAMultiPolygon.getValue()).isEqualTo(6);
    }

    @Test
    void gaia_point_z_type_value_must_be_1001() {
        assertThat(GeometryType.GAIAPointZ.getValue()).isEqualTo(1001);
    }

    @Test
    void gaia_multi_line_string_z_type_value_must_be_1005() {
        assertThat(GeometryType.GAIAMultiLineStringZ.getValue()).isEqualTo(1005);
    }

    @Test
    void gaia_multi_polygon_z_type_value_must_be_1006() {
        assertThat(GeometryType.GAIAMultiPolygonZ.getValue()).isEqualTo(1006);
    }

    // ── GeoHeader 格式 ─────────────────────────────────────────────────────

    @Test
    void geo_point_type_value_must_be_1() {
        assertThat(GeometryType.GeoPoint.getValue()).isEqualTo(1);
    }

    @Test
    void geo_line_type_value_must_be_3() {
        assertThat(GeometryType.GeoLine.getValue()).isEqualTo(3);
    }

    @Test
    void geo_region_type_value_must_be_5() {
        assertThat(GeometryType.GeoRegion.getValue()).isEqualTo(5);
    }

    @Test
    void geo_rect_type_value_must_be_12() {
        assertThat(GeometryType.GeoRect.getValue()).isEqualTo(12);
    }

    @Test
    void geo_circle_type_value_must_be_15() {
        assertThat(GeometryType.GeoCircle.getValue()).isEqualTo(15);
    }

    @Test
    void geo_ellipse_type_value_must_be_20() {
        assertThat(GeometryType.GeoEllipse.getValue()).isEqualTo(20);
    }

    @Test
    void geo_pie_type_value_must_be_21() {
        assertThat(GeometryType.GeoPie.getValue()).isEqualTo(21);
    }

    @Test
    void geo_arc_type_value_must_be_24() {
        assertThat(GeometryType.GeoArc.getValue()).isEqualTo(24);
    }

    @Test
    void geo_elliptic_arc_type_value_must_be_25() {
        assertThat(GeometryType.GeoEllipticArc.getValue()).isEqualTo(25);
    }

    @Test
    void geo_cardinal_type_value_must_be_27() {
        assertThat(GeometryType.GeoCardinal.getValue()).isEqualTo(27);
    }

    @Test
    void geo_curve_type_value_must_be_28() {
        assertThat(GeometryType.GeoCurve.getValue()).isEqualTo(28);
    }

    @Test
    void geo_b_spline_type_value_must_be_29() {
        assertThat(GeometryType.GeoBSpline.getValue()).isEqualTo(29);
    }

    // ── fromValue 工厂方法 ──────────────────────────────────────────────────

    @Test
    void from_value_must_throw_for_unknown_value() {
        assertThatThrownBy(() -> GeometryType.fromCadValue(999))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("999");
    }

    @Test
    void from_cad_value_must_return_correct_enum() {
        assertThat(GeometryType.fromCadValue(1)).isEqualTo(GeometryType.GeoPoint);
        assertThat(GeometryType.fromCadValue(3)).isEqualTo(GeometryType.GeoLine);
        assertThat(GeometryType.fromCadValue(5)).isEqualTo(GeometryType.GeoRegion);
        assertThat(GeometryType.fromCadValue(12)).isEqualTo(GeometryType.GeoRect);
        assertThat(GeometryType.fromCadValue(15)).isEqualTo(GeometryType.GeoCircle);
        assertThat(GeometryType.fromCadValue(29)).isEqualTo(GeometryType.GeoBSpline);
    }

    @Test
    void from_gaia_value_must_return_correct_enum() {
        assertThat(GeometryType.fromGaiaValue(1)).isEqualTo(GeometryType.GAIAPoint);
        assertThat(GeometryType.fromGaiaValue(5)).isEqualTo(GeometryType.GAIAMultiLineString);
        assertThat(GeometryType.fromGaiaValue(6)).isEqualTo(GeometryType.GAIAMultiPolygon);
        assertThat(GeometryType.fromGaiaValue(1001)).isEqualTo(GeometryType.GAIAPointZ);
        assertThat(GeometryType.fromGaiaValue(1005)).isEqualTo(GeometryType.GAIAMultiLineStringZ);
        assertThat(GeometryType.fromGaiaValue(1006)).isEqualTo(GeometryType.GAIAMultiPolygonZ);
    }

    @Test
    void from_gaia_value_must_throw_for_unknown_value() {
        assertThatThrownBy(() -> GeometryType.fromGaiaValue(999))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("999");
    }
}
