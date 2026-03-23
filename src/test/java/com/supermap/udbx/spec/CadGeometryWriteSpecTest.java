package com.supermap.udbx.spec;

import com.supermap.udbx.core.GeometryType;
import com.supermap.udbx.geometry.cad.CadGeometry;
import com.supermap.udbx.geometry.cad.CadGeometryReader;
import com.supermap.udbx.geometry.cad.CadGeometryWriter;
import com.supermap.udbx.geometry.cad.StyleFill;
import com.supermap.udbx.geometry.cad.StyleLine;
import com.supermap.udbx.geometry.cad.StyleMarker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Spec 测试：CAD Geometry 编码往返（encode → decode）验证。
 *
 * <p>每个测试构造一个 {@link CadGeometry} 对象，调用 {@link CadGeometryWriter#write}
 * 编码为字节数组，再用 {@link CadGeometryReader#read} 解码，断言往返结果与原始对象等价。
 */
class CadGeometryWriteSpecTest {

    // ── 点 ──────────────────────────────────────────────────────────────────

    @Test
    void geopoint_roundtrip_no_style() {
        CadGeometry orig = new CadGeometry.GeoPoint(116.39, 39.91, null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoPoint.class);
        CadGeometry.GeoPoint pt = (CadGeometry.GeoPoint) decoded;
        assertThat(pt.x()).isCloseTo(116.39, offset(1e-9));
        assertThat(pt.y()).isCloseTo(39.91, offset(1e-9));
        assertThat(pt.style()).isNull();
    }

    @Test
    void geopoint3d_roundtrip_no_style() {
        CadGeometry orig = new CadGeometry.GeoPoint3D(1.0, 2.0, 3.5, null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoPoint3D.class);
        CadGeometry.GeoPoint3D pt = (CadGeometry.GeoPoint3D) decoded;
        assertThat(pt.x()).isCloseTo(1.0, offset(1e-9));
        assertThat(pt.y()).isCloseTo(2.0, offset(1e-9));
        assertThat(pt.z()).isCloseTo(3.5, offset(1e-9));
    }

    // ── 点 + StyleMarker ─────────────────────────────────────────────────────

    @Test
    void geopoint_with_stylemarker_roundtrip() {
        StyleMarker sm = new StyleMarker(3, 10, 0, 0xFF0000FF,
                10, 10, (byte) 255, (byte) 0, (short) 0, (short) 0, (short) 0, 0xFFFFFFFF);
        CadGeometry orig = new CadGeometry.GeoPoint(5.0, 6.0, sm);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoPoint.class);
        CadGeometry.GeoPoint pt = (CadGeometry.GeoPoint) decoded;
        assertThat(pt.style()).isInstanceOf(StyleMarker.class);
        StyleMarker result = (StyleMarker) pt.style();
        assertThat(result.markerStyle()).isEqualTo(3);
        assertThat(result.markerSize()).isEqualTo(10);
        assertThat(result.markerColor()).isEqualTo(0xFF0000FF);
        assertThat(result.fillOpaqueRate()).isEqualTo((byte) 255);
    }

    // ── 线 ──────────────────────────────────────────────────────────────────

    @Test
    void geoline_roundtrip_no_style() {
        CadGeometry orig = new CadGeometry.GeoLine(
                2, new int[]{2, 1},
                new double[]{0.0, 1.0, 0.0},
                new double[]{0.0, 0.0, 1.0},
                null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoLine.class);
        CadGeometry.GeoLine line = (CadGeometry.GeoLine) decoded;
        assertThat(line.numSub()).isEqualTo(2);
        assertThat(line.subPointCounts()).containsExactly(2, 1);
        assertThat(line.xs()).containsExactly(0.0, 1.0, 0.0);
        assertThat(line.ys()).containsExactly(0.0, 0.0, 1.0);
    }

    @Test
    void geoline_with_styleline_roundtrip() {
        StyleLine sl = new StyleLine(2, 50, 0xFF00FF00);
        CadGeometry orig = new CadGeometry.GeoLine(
                1, new int[]{2},
                new double[]{0.0, 1.0},
                new double[]{0.0, 1.0},
                sl);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        CadGeometry.GeoLine line = (CadGeometry.GeoLine) decoded;
        assertThat(line.style()).isInstanceOf(StyleLine.class);
        StyleLine result = (StyleLine) line.style();
        assertThat(result.lineStyle()).isEqualTo(2);
        assertThat(result.lineWidth()).isEqualTo(50);
        assertThat(result.lineColor()).isEqualTo(0xFF00FF00);
    }

    @Test
    void geoline3d_roundtrip() {
        CadGeometry orig = new CadGeometry.GeoLine3D(
                1, new int[]{2},
                new double[]{0.0, 1.0},
                new double[]{0.0, 1.0},
                new double[]{0.5, 1.5},
                null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoLine3D.class);
        CadGeometry.GeoLine3D line = (CadGeometry.GeoLine3D) decoded;
        assertThat(line.zs()).containsExactly(0.5, 1.5);
    }

    // ── 面 ──────────────────────────────────────────────────────────────────

    @Test
    void georegion_with_stylefill_roundtrip() {
        StyleFill sf = new StyleFill(1, 50, 0xFF000000, 2, 0xFF00FF00, 0xFF0000FF,
                (byte) 200, (byte) 0, (short) 0, (short) 0, (short) 0);
        CadGeometry orig = new CadGeometry.GeoRegion(
                1, new int[]{5},
                new double[]{0.0, 1.0, 1.0, 0.0, 0.0},
                new double[]{0.0, 0.0, 1.0, 1.0, 0.0},
                sf);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoRegion.class);
        CadGeometry.GeoRegion region = (CadGeometry.GeoRegion) decoded;
        assertThat(region.numSub()).isEqualTo(1);
        assertThat(region.subPointCounts()).containsExactly(5);
        assertThat(region.style()).isInstanceOf(StyleFill.class);
        StyleFill result = (StyleFill) region.style();
        assertThat(result.fillStyle()).isEqualTo(2);
        assertThat(result.fillOpaquerate()).isEqualTo((byte) 200);
    }

    @Test
    void georegion3d_roundtrip() {
        CadGeometry orig = new CadGeometry.GeoRegion3D(
                1, new int[]{3},
                new double[]{0.0, 1.0, 0.5},
                new double[]{0.0, 0.0, 1.0},
                new double[]{10.0, 10.0, 10.0},
                null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoRegion3D.class);
        CadGeometry.GeoRegion3D r = (CadGeometry.GeoRegion3D) decoded;
        assertThat(r.zs()).containsExactly(10.0, 10.0, 10.0);
    }

    // ── 参数化几何 ────────────────────────────────────────────────────────────

    @Test
    void georect_roundtrip() {
        CadGeometry orig = new CadGeometry.GeoRect(5.0, 5.0, 10.0, 8.0, 450, null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoRect.class);
        CadGeometry.GeoRect rect = (CadGeometry.GeoRect) decoded;
        assertThat(rect.centerX()).isCloseTo(5.0, offset(1e-9));
        assertThat(rect.centerY()).isCloseTo(5.0, offset(1e-9));
        assertThat(rect.width()).isCloseTo(10.0, offset(1e-9));
        assertThat(rect.height()).isCloseTo(8.0, offset(1e-9));
        assertThat(rect.angle()).isEqualTo(450);
    }

    @Test
    void geocircle_roundtrip() {
        CadGeometry orig = new CadGeometry.GeoCircle(3.0, 4.0, 5.0, null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoCircle.class);
        CadGeometry.GeoCircle c = (CadGeometry.GeoCircle) decoded;
        assertThat(c.radius()).isCloseTo(5.0, offset(1e-9));
    }

    @Test
    void geoellipse_roundtrip() {
        CadGeometry orig = new CadGeometry.GeoEllipse(0.0, 0.0, 10.0, 6.0, 300, null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoEllipse.class);
        CadGeometry.GeoEllipse e = (CadGeometry.GeoEllipse) decoded;
        assertThat(e.semimajoraxis()).isCloseTo(10.0, offset(1e-9));
        assertThat(e.semiminoraxis()).isCloseTo(6.0, offset(1e-9));
        assertThat(e.angle()).isEqualTo(300);
    }

    @Test
    void geopie_roundtrip() {
        CadGeometry orig = new CadGeometry.GeoPie(0.0, 0.0, 8.0, 5.0, 0, 300, 1500, null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoPie.class);
        CadGeometry.GeoPie pie = (CadGeometry.GeoPie) decoded;
        assertThat(pie.startangle()).isEqualTo(300);
        assertThat(pie.endangle()).isEqualTo(1500);
    }

    @Test
    void geoarc_roundtrip() {
        CadGeometry orig = new CadGeometry.GeoArc(0.0, 0.0, 1.0, 2.0, 2.0, 0.0, null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoArc.class);
        CadGeometry.GeoArc arc = (CadGeometry.GeoArc) decoded;
        assertThat(arc.middleX()).isCloseTo(1.0, offset(1e-9));
        assertThat(arc.middleY()).isCloseTo(2.0, offset(1e-9));
    }

    @Test
    void geoellipticarc_roundtrip() {
        CadGeometry orig = new CadGeometry.GeoEllipticArc(0.0, 0.0, 6.0, 3.0, 0, 100, 2700, null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoEllipticArc.class);
        CadGeometry.GeoEllipticArc ea = (CadGeometry.GeoEllipticArc) decoded;
        assertThat(ea.startangle()).isEqualTo(100);
        assertThat(ea.endangle()).isEqualTo(2700);
    }

    // ── 曲线对象 ──────────────────────────────────────────────────────────────

    @Test
    void geocardinal_roundtrip() {
        CadGeometry orig = new CadGeometry.GeoCurveObject(
                GeometryType.GeoCardinal,
                new double[]{0.0, 1.0, 2.0},
                new double[]{0.0, 1.0, 0.0},
                null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoCurveObject.class);
        CadGeometry.GeoCurveObject c = (CadGeometry.GeoCurveObject) decoded;
        assertThat(c.geoType()).isEqualTo(GeometryType.GeoCardinal);
        assertThat(c.xs()).containsExactly(0.0, 1.0, 2.0);
    }

    @Test
    void geocurve_roundtrip() {
        CadGeometry orig = new CadGeometry.GeoCurveObject(
                GeometryType.GeoCurve,
                new double[]{0.0, 0.5, 1.0},
                new double[]{1.0, 2.0, 1.0},
                null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        assertThat(decoded).isInstanceOf(CadGeometry.GeoCurveObject.class);
        assertThat(((CadGeometry.GeoCurveObject) decoded).geoType()).isEqualTo(GeometryType.GeoCurve);
    }

    @Test
    void geobspline_roundtrip() {
        CadGeometry orig = new CadGeometry.GeoCurveObject(
                GeometryType.GeoBSpline,
                new double[]{0.0, 1.0, 2.0, 3.0},
                new double[]{0.0, 1.0, 1.0, 0.0},
                null);
        CadGeometry decoded = CadGeometryReader.read(CadGeometryWriter.write(orig));

        CadGeometry.GeoCurveObject c = (CadGeometry.GeoCurveObject) decoded;
        assertThat(c.geoType()).isEqualTo(GeometryType.GeoBSpline);
        assertThat(c.ys()).containsExactly(0.0, 1.0, 1.0, 0.0);
    }
}
