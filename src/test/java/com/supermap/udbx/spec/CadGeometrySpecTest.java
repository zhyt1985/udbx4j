package com.supermap.udbx.spec;

import com.supermap.udbx.geometry.cad.CadGeometry;
import com.supermap.udbx.geometry.cad.CadGeometryReader;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Spec 测试：验证各类 CAD 参数化几何对象解码。
 *
 * <p>对应白皮书 §4.3.8 ~ §4.3.15。
 */
class CadGeometrySpecTest {

    // ── 工具方法 ──────────────────────────────────────────────────────────────

    /** 写 GeoHeader（geoType + styleSize=0），返回位置后的 ByteBuffer。 */
    private static ByteBuffer header(ByteBuffer buf, int geoType) {
        buf.putInt(geoType);
        buf.putInt(0); // styleSize = 0
        return buf;
    }

    // ── GeoRect（type=12）────────────────────────────────────────────────────

    /**
     * GeoRect: GeoHeader(8) + pntCenter(16) + width(8) + height(8) + angle(4) + reserved(4) = 48 bytes
     */
    @Test
    void georect_must_decode_correctly() {
        ByteBuffer buf = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        header(buf, 12);
        buf.putDouble(10.0); buf.putDouble(20.0); // center
        buf.putDouble(5.0);                        // width
        buf.putDouble(3.0);                        // height
        buf.putInt(450);                            // angle = 45.0 degrees * 10
        buf.putInt(0);                              // reserved

        CadGeometry geom = CadGeometryReader.read(buf.array());

        assertThat(geom).isInstanceOf(CadGeometry.GeoRect.class);
        CadGeometry.GeoRect rect = (CadGeometry.GeoRect) geom;
        assertThat(rect.centerX()).isCloseTo(10.0, offset(1e-9));
        assertThat(rect.centerY()).isCloseTo(20.0, offset(1e-9));
        assertThat(rect.width()).isCloseTo(5.0, offset(1e-9));
        assertThat(rect.height()).isCloseTo(3.0, offset(1e-9));
        assertThat(rect.angle()).isEqualTo(450);
    }

    // ── GeoCircle（type=15）──────────────────────────────────────────────────

    /**
     * GeoCircle: GeoHeader(8) + pntCenter(16) + radius(8) = 32 bytes
     */
    @Test
    void geocircle_must_decode_correctly() {
        ByteBuffer buf = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        header(buf, 15);
        buf.putDouble(5.0); buf.putDouble(6.0); // center
        buf.putDouble(2.5);                      // radius

        CadGeometry geom = CadGeometryReader.read(buf.array());

        assertThat(geom).isInstanceOf(CadGeometry.GeoCircle.class);
        CadGeometry.GeoCircle circle = (CadGeometry.GeoCircle) geom;
        assertThat(circle.centerX()).isCloseTo(5.0, offset(1e-9));
        assertThat(circle.centerY()).isCloseTo(6.0, offset(1e-9));
        assertThat(circle.radius()).isCloseTo(2.5, offset(1e-9));
    }

    // ── GeoEllipse（type=20）─────────────────────────────────────────────────

    /**
     * GeoEllipse: GeoHeader(8) + pntCenter(16) + semimajor(8) + semiminor(8) + angle(4) + reserved(4) = 48 bytes
     */
    @Test
    void geoellipse_must_decode_correctly() {
        ByteBuffer buf = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        header(buf, 20);
        buf.putDouble(1.0); buf.putDouble(2.0); // center
        buf.putDouble(4.0);                      // semimajoraxis
        buf.putDouble(2.0);                      // semiminoraxis
        buf.putInt(900);                          // angle = 90.0 degrees * 10
        buf.putInt(0);                            // reserved

        CadGeometry geom = CadGeometryReader.read(buf.array());

        assertThat(geom).isInstanceOf(CadGeometry.GeoEllipse.class);
        CadGeometry.GeoEllipse ellipse = (CadGeometry.GeoEllipse) geom;
        assertThat(ellipse.semimajoraxis()).isCloseTo(4.0, offset(1e-9));
        assertThat(ellipse.semiminoraxis()).isCloseTo(2.0, offset(1e-9));
        assertThat(ellipse.angle()).isEqualTo(900);
    }

    // ── GeoPie（type=21）─────────────────────────────────────────────────────

    /**
     * GeoPie: GeoHeader(8) + pntCenter(16) + semimajor(8) + semiminor(8) + rotationangle(4)
     *         + startangle(4) + endangle(4) + reserved(4) = 56 bytes
     */
    @Test
    void geopie_must_decode_correctly() {
        ByteBuffer buf = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);
        header(buf, 21);
        buf.putDouble(0.0); buf.putDouble(0.0); // center
        buf.putDouble(3.0);                      // semimajoraxis
        buf.putDouble(2.0);                      // semiminoraxis
        buf.putInt(0);                            // rotationangle
        buf.putInt(0);                            // startangle = 0 degrees * 10
        buf.putInt(1800);                         // endangle = 180 degrees * 10
        buf.putInt(0);                            // reserved

        CadGeometry geom = CadGeometryReader.read(buf.array());

        assertThat(geom).isInstanceOf(CadGeometry.GeoPie.class);
        CadGeometry.GeoPie pie = (CadGeometry.GeoPie) geom;
        assertThat(pie.semimajoraxis()).isCloseTo(3.0, offset(1e-9));
        assertThat(pie.startangle()).isEqualTo(0);
        assertThat(pie.endangle()).isEqualTo(1800);
    }

    // ── GeoArc（type=24）─────────────────────────────────────────────────────

    /**
     * GeoArc: GeoHeader(8) + pntStart(16) + pntMiddle(16) + pntEnd(16) = 56 bytes
     */
    @Test
    void geoarc_must_decode_correctly() {
        ByteBuffer buf = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);
        header(buf, 24);
        buf.putDouble(0.0); buf.putDouble(0.0); // start
        buf.putDouble(1.0); buf.putDouble(1.0); // middle
        buf.putDouble(2.0); buf.putDouble(0.0); // end

        CadGeometry geom = CadGeometryReader.read(buf.array());

        assertThat(geom).isInstanceOf(CadGeometry.GeoArc.class);
        CadGeometry.GeoArc arc = (CadGeometry.GeoArc) geom;
        assertThat(arc.startX()).isCloseTo(0.0, offset(1e-9));
        assertThat(arc.startY()).isCloseTo(0.0, offset(1e-9));
        assertThat(arc.middleX()).isCloseTo(1.0, offset(1e-9));
        assertThat(arc.middleY()).isCloseTo(1.0, offset(1e-9));
        assertThat(arc.endX()).isCloseTo(2.0, offset(1e-9));
        assertThat(arc.endY()).isCloseTo(0.0, offset(1e-9));
    }

    // ── GeoEllipticArc（type=25）─────────────────────────────────────────────

    /**
     * GeoEllipticArc: GeoHeader(8) + pntCenter(16) + semimajor(8) + semiminor(8) + rotationangle(4)
     *                  + startangle(4) + endangle(4) + reserved(4) = 56 bytes
     */
    @Test
    void geoellipticarc_must_decode_correctly() {
        ByteBuffer buf = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);
        header(buf, 25);
        buf.putDouble(0.0); buf.putDouble(0.0); // center
        buf.putDouble(5.0);                      // semimajoraxis
        buf.putDouble(3.0);                      // semiminoraxis
        buf.putInt(0);                            // rotationangle
        buf.putInt(0);                            // startangle
        buf.putInt(900);                          // endangle = 90°×10
        buf.putInt(0);                            // reserved

        CadGeometry geom = CadGeometryReader.read(buf.array());

        assertThat(geom).isInstanceOf(CadGeometry.GeoEllipticArc.class);
        CadGeometry.GeoEllipticArc arc = (CadGeometry.GeoEllipticArc) geom;
        assertThat(arc.semimajoraxis()).isCloseTo(5.0, offset(1e-9));
        assertThat(arc.endangle()).isEqualTo(900);
    }

    // ── GeoCurve / GeoBSpline / GeoCardinal（同结构）──────────────────────────

    /**
     * CurveObject: GeoHeader(8) + numPnts(4) + pnts[numPnts](numPnts*16) bytes
     * 本测试使用 GeoBSpline（type=29）验证。
     */
    @Test
    void geobspline_must_decode_correctly() {
        int numPnts = 4;
        int size = 8 + 4 + numPnts * 16; // = 76
        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        header(buf, 29); // GeoBSpline
        buf.putInt(numPnts);
        buf.putDouble(0.0); buf.putDouble(0.0);
        buf.putDouble(1.0); buf.putDouble(2.0);
        buf.putDouble(3.0); buf.putDouble(2.0);
        buf.putDouble(4.0); buf.putDouble(0.0);

        CadGeometry geom = CadGeometryReader.read(buf.array());

        assertThat(geom).isInstanceOf(CadGeometry.GeoCurveObject.class);
        CadGeometry.GeoCurveObject curve = (CadGeometry.GeoCurveObject) geom;
        assertThat(curve.xs()).hasSize(4);
        assertThat(curve.xs()[0]).isCloseTo(0.0, offset(1e-9));
        assertThat(curve.xs()[3]).isCloseTo(4.0, offset(1e-9));
        assertThat(curve.ys()[1]).isCloseTo(2.0, offset(1e-9));
    }

    @Test
    void geocardinal_must_decode_correctly() {
        int numPnts = 3;
        int size = 8 + 4 + numPnts * 16;
        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        header(buf, 27); // GeoCardinal
        buf.putInt(numPnts);
        buf.putDouble(1.0); buf.putDouble(0.0);
        buf.putDouble(2.0); buf.putDouble(3.0);
        buf.putDouble(3.0); buf.putDouble(0.0);

        CadGeometry geom = CadGeometryReader.read(buf.array());

        assertThat(geom).isInstanceOf(CadGeometry.GeoCurveObject.class);
        CadGeometry.GeoCurveObject curve = (CadGeometry.GeoCurveObject) geom;
        assertThat(curve.xs()).hasSize(3);
        assertThat(curve.ys()[1]).isCloseTo(3.0, offset(1e-9));
    }
}
