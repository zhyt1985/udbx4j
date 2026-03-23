package com.supermap.udbx.spec;

import com.supermap.udbx.geometry.cad.CadGeometry;
import com.supermap.udbx.geometry.cad.CadGeometryReader;
import com.supermap.udbx.geometry.cad.CadStyle;
import com.supermap.udbx.geometry.cad.StyleFill;
import com.supermap.udbx.geometry.cad.StyleLine;
import com.supermap.udbx.geometry.cad.StyleMarker;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Spec 测试：验证 CAD GeoHeader 及 Style 解码。
 *
 * <p>对应白皮书 §4.3（CAD 数据集中存储的对象）。
 *
 * <p>GeoHeader 结构（8 + styleSize 字节）：
 * <pre>
 *   int32 geoType
 *   int32 styleSize
 *   Style style   (styleSize 字节)
 * </pre>
 */
class CadGeoHeaderSpecTest {

    // ──────────────────────────────────────────────────────────────────────────
    // 辅助：构建 GeoPoint（geoType=1）+ 无 Style（styleSize=0）
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 构建 GeoPoint blob，无 Style。
     * 格式：geoType(4) + styleSize(4) + x(8) + y(8) = 24 bytes
     */
    private static byte[] buildGeoPointNoStyle(double x, double y) {
        ByteBuffer buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(1);      // geoType = GeoPoint
        buf.putInt(0);      // styleSize = 0
        buf.putDouble(x);
        buf.putDouble(y);
        return buf.array();
    }

    /**
     * 构建 GeoLine blob（geoType=3），无 Style，2条子线。
     *
     * <p>结构：geoType(4) + styleSize(4) + numSub(4) + subCount[2](8) + pts[3](48) = 68 bytes
     * <ul>
     *   <li>Line 0: (0,0)→(1,0) — 2 点</li>
     *   <li>Line 1: (0,1) — 1 点</li>
     * </ul>
     */
    private static byte[] buildGeoLine2SubNoStyle() {
        // numSub=2, subPointCounts=[2,1], pts=3*16=48
        // header(8) + numSub(4) + subPtCounts(8) + pts(48) = 68
        ByteBuffer buf = ByteBuffer.allocate(68).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(3);   // geoType = GeoLine
        buf.putInt(0);   // styleSize
        buf.putInt(2);   // numSub
        buf.putInt(2);   // sub0: 2 points
        buf.putInt(1);   // sub1: 1 point
        // points: (0,0), (1,0), (0,1)
        buf.putDouble(0.0); buf.putDouble(0.0);
        buf.putDouble(1.0); buf.putDouble(0.0);
        buf.putDouble(0.0); buf.putDouble(1.0);
        return buf.array();
    }

    /**
     * 构建 StyleLine 编码字节数组（用于 GeoLine style 验证）。
     *
     * <p>StyleLine 结构：
     * <pre>
     *   lineStyle(4) + lineWidth(4) + lineColor(4) + reservedLength(1) + reservedData[reservedLength+4]
     * </pre>
     * 本测试使用 reservedLength=0，故 reservedData 占 4 字节（固定）。
     * 总计：4+4+4+1+4 = 17 bytes
     */
    private static byte[] buildStyleLine(int lineStyle, int lineWidth, int colorABGR) {
        ByteBuffer buf = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(lineStyle);
        buf.putInt(lineWidth);
        buf.putInt(colorABGR);   // ABGR: alpha=0xFF, blue=0x00, green=0x00, red=0xFF → 0xFF0000FF
        buf.put((byte) 0);       // reservedLength = 0
        buf.putInt(0);           // reservedData[0+4] = 4 bytes of zeros
        return buf.array();
    }

    /**
     * 构建带 StyleLine 的 GeoLine blob。
     *
     * <p>GeoLine = geoType(4) + styleSize(4) + StyleLine(17) + numSub(4) + subPtCounts(4) + pts(16) = 49 bytes
     */
    private static byte[] buildGeoLineWithStyleLine() {
        byte[] styleBytes = buildStyleLine(1, 100, 0xFF0000FF); // lineStyle=1, width=100, red line
        int size = 4 + 4 + styleBytes.length + 4 + 4 + 32; // geoType+styleSize+style+numSub+sub0count+2pts*16 = 65
        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(3);                   // geoType = GeoLine
        buf.putInt(styleBytes.length);   // styleSize = 17
        buf.put(styleBytes);             // StyleLine
        buf.putInt(1);                   // numSub = 1
        buf.putInt(2);                   // sub0: 2 points
        buf.putDouble(0.0); buf.putDouble(0.0);
        buf.putDouble(1.0); buf.putDouble(1.0);
        return buf.array();
    }

    /**
     * 构建带 StyleFill 的 GeoRegion blob。
     *
     * <p>StyleFill 结构（最小化，两个 reservedLength=0）：
     * <pre>
     *   lineStyle(4)+lineWidth(4)+lineColor(4)+fillStyle(4)+fillForecolor(4)+fillBackcolor(4)
     *   +fillOpaquerate(1)+fillGadientType(1)+fillAngle(2)+fillCenterOffsetX(2)+fillCenterOffsetY(2)
     *   +reserved1Length(1)+reserved1Data[0+4]
     *   +reserved2Length(1)+reserved2Data[0+4]
     *   = 4+4+4+4+4+4+1+1+2+2+2+1+4+1+4 = 42 bytes
     * </pre>
     */
    private static byte[] buildGeoRegionWithStyleFill() {
        // StyleFill: 42 bytes
        ByteBuffer styleBuf = ByteBuffer.allocate(42).order(ByteOrder.LITTLE_ENDIAN);
        styleBuf.putInt(1);           // lineStyle
        styleBuf.putInt(50);          // lineWidth
        styleBuf.putInt(0xFF000000);  // lineColor (ABGR)
        styleBuf.putInt(2);           // fillStyle
        styleBuf.putInt(0xFF00FF00);  // fillForecolor (ABGR)
        styleBuf.putInt(0xFF0000FF);  // fillBackcolor (ABGR)
        styleBuf.put((byte) 200);     // fillOpaquerate
        styleBuf.put((byte) 0);       // fillGadientType
        styleBuf.putShort((short) 0); // fillAngle
        styleBuf.putShort((short) 0); // fillCenterOffsetX
        styleBuf.putShort((short) 0); // fillCenterOffsetY
        styleBuf.put((byte) 0);       // reserved1Length = 0
        styleBuf.putInt(0);           // reserved1Data[4]
        styleBuf.put((byte) 0);       // reserved2Length = 0
        styleBuf.putInt(0);           // reserved2Data[4]
        byte[] styleBytes = styleBuf.array();

        // GeoRegion: exterior ring 5 pts (closed square)
        // header(8) + styleSize(42) + numSub(4) + subPtCount[1](4) + pts[5](40) = 98
        ByteBuffer buf = ByteBuffer.allocate(8 + styleBytes.length + 4 + 4 + 80)  // 5pts*16=80
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(5);                   // geoType = GeoRegion
        buf.putInt(styleBytes.length);   // styleSize = 42
        buf.put(styleBytes);
        buf.putInt(1);                   // numSub = 1
        buf.putInt(5);                   // sub0: 5 points
        buf.putDouble(0.0); buf.putDouble(0.0);
        buf.putDouble(1.0); buf.putDouble(0.0);
        buf.putDouble(1.0); buf.putDouble(1.0);
        buf.putDouble(0.0); buf.putDouble(1.0);
        buf.putDouble(0.0); buf.putDouble(0.0); // 闭合
        return buf.array();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 测试：GeoPoint 无 Style
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void geopoint_no_style_must_decode_correctly() {
        byte[] blob = buildGeoPointNoStyle(116.39, 39.91);

        CadGeometry geom = CadGeometryReader.read(blob);

        assertThat(geom).isInstanceOf(CadGeometry.GeoPoint.class);
        CadGeometry.GeoPoint pt = (CadGeometry.GeoPoint) geom;
        assertThat(pt.x()).isCloseTo(116.39, offset(1e-9));
        assertThat(pt.y()).isCloseTo(39.91, offset(1e-9));
        assertThat(pt.style()).isNull();
    }

    @Test
    void geoline_no_style_must_decode_sub_structure() {
        byte[] blob = buildGeoLine2SubNoStyle();

        CadGeometry geom = CadGeometryReader.read(blob);

        assertThat(geom).isInstanceOf(CadGeometry.GeoLine.class);
        CadGeometry.GeoLine line = (CadGeometry.GeoLine) geom;
        assertThat(line.numSub()).isEqualTo(2);
        assertThat(line.subPointCounts()).containsExactly(2, 1);
        assertThat(line.xs()).containsExactly(0.0, 1.0, 0.0);
        assertThat(line.ys()).containsExactly(0.0, 0.0, 1.0);
        assertThat(line.style()).isNull();
    }

    @Test
    void geoline_with_styleline_must_decode_style() {
        byte[] blob = buildGeoLineWithStyleLine();

        CadGeometry geom = CadGeometryReader.read(blob);

        assertThat(geom).isInstanceOf(CadGeometry.GeoLine.class);
        CadGeometry.GeoLine line = (CadGeometry.GeoLine) geom;
        assertThat(line.style()).isInstanceOf(StyleLine.class);
        StyleLine sl = (StyleLine) line.style();
        assertThat(sl.lineStyle()).isEqualTo(1);
        assertThat(sl.lineWidth()).isEqualTo(100);
        assertThat(sl.lineColor()).isEqualTo(0xFF0000FF);
    }

    @Test
    void georegion_with_stylefill_must_decode_style() {
        byte[] blob = buildGeoRegionWithStyleFill();

        CadGeometry geom = CadGeometryReader.read(blob);

        assertThat(geom).isInstanceOf(CadGeometry.GeoRegion.class);
        CadGeometry.GeoRegion region = (CadGeometry.GeoRegion) geom;
        assertThat(region.style()).isInstanceOf(StyleFill.class);
        StyleFill sf = (StyleFill) region.style();
        assertThat(sf.lineStyle()).isEqualTo(1);
        assertThat(sf.lineWidth()).isEqualTo(50);
        assertThat(sf.fillStyle()).isEqualTo(2);
        assertThat(sf.fillOpaquerate()).isEqualTo((byte) 200);
        // 5 points (closed square)
        assertThat(region.numSub()).isEqualTo(1);
        assertThat(region.subPointCounts()).containsExactly(5);
    }
}
