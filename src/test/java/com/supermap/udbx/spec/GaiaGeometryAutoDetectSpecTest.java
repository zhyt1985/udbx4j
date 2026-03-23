package com.supermap.udbx.spec;

import com.supermap.udbx.geometry.gaia.GaiaGeometryReader;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 测试：验证 GaiaGeometryReader.readMultiPolygonAuto() 的各路径。
 *
 * <p>对应白皮书 §4.2.5（geoType=6，2D）和 §4.2.7（geoType=1006，3D）。
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>geoType=6（2D MultiPolygon）正常路径</li>
 *   <li>geoType=1006（3D MultiPolygonZ）正常路径</li>
 *   <li>geoType 不合法时抛 IllegalArgumentException</li>
 *   <li>gaiaStart 不是 0x00 时抛 IllegalArgumentException</li>
 *   <li>byteOrder 不是 0x01 时抛 IllegalArgumentException</li>
 *   <li>gaiaMBR 不是 0x7c 时抛 IllegalArgumentException</li>
 * </ul>
 */
class GaiaGeometryAutoDetectSpecTest {

    // ── 构建辅助方法 ──────────────────────────────────────────────────────────

    /**
     * 写入 GAIA 头部（43 bytes）。
     * 布局：gaiaStart(1) + byteOrder(1) + srid(4) + mbr(32) + gaiaMBR(1) + geoType(4)
     */
    private static void writeHeader(ByteBuffer buf, int geoType,
                                    double minX, double minY, double maxX, double maxY) {
        buf.put((byte) 0x00);    // gaiaStart
        buf.put((byte) 0x01);    // byteOrder = little-endian
        buf.putInt(4326);        // srid
        buf.putDouble(minX);
        buf.putDouble(minY);
        buf.putDouble(maxX);
        buf.putDouble(maxY);
        buf.put((byte) 0x7c);    // gaiaMBR marker
        buf.putInt(geoType);
    }

    /**
     * 构建 GAIA MultiPolygon 2D 字节数组（geoType=6）。
     *
     * <p>内容：srid=4326，1个多边形，外环5点（含闭合）：
     * (0,0)→(1,0)→(1,1)→(0,1)→(0,0)
     *
     * <p>字节大小：头部43 + numPolygons(4) + entityMark(1)+geoType(4)+numRings(4) + numPoints(4)+5*16 + 尾部(1) = 141
     */
    private static byte[] buildMultiPolygon2D() {
        ByteBuffer buf = ByteBuffer.allocate(141).order(ByteOrder.LITTLE_ENDIAN);
        writeHeader(buf, 6, 0.0, 0.0, 1.0, 1.0);

        buf.putInt(1);           // numPolygons = 1
        buf.put((byte) 0x69);   // gaiaEntityMark
        buf.putInt(3);           // geoType = Polygon
        buf.putInt(1);           // numRings = 1 (exterior only)
        buf.putInt(5);           // shell numPoints = 5
        buf.putDouble(0.0); buf.putDouble(0.0);
        buf.putDouble(1.0); buf.putDouble(0.0);
        buf.putDouble(1.0); buf.putDouble(1.0);
        buf.putDouble(0.0); buf.putDouble(1.0);
        buf.putDouble(0.0); buf.putDouble(0.0);  // 闭合

        buf.put((byte) 0xFE);
        return buf.array();
    }

    /**
     * 构建 GAIA MultiPolygonZ 3D 字节数组（geoType=1006）。
     *
     * <p>内容：srid=4326，1个多边形，外环5点（含闭合），Z=5.0：
     * (0,0,5)→(1,0,5)→(1,1,5)→(0,1,5)→(0,0,5)
     *
     * <p>字节大小：头部43 + numPolygons(4) + entityMark(1)+geoType(4)+numRings(4) + numPoints(4)+5*24 + 尾部(1) = 181
     */
    private static byte[] buildMultiPolygonZ() {
        ByteBuffer buf = ByteBuffer.allocate(181).order(ByteOrder.LITTLE_ENDIAN);
        writeHeader(buf, 1006, 0.0, 0.0, 1.0, 1.0);

        buf.putInt(1);           // numPolygons = 1
        buf.put((byte) 0x69);   // gaiaEntityMark
        buf.putInt(1003);        // geoType = PolygonZ
        buf.putInt(1);           // numRings = 1
        buf.putInt(5);           // shell numPoints = 5
        buf.putDouble(0.0); buf.putDouble(0.0); buf.putDouble(5.0);
        buf.putDouble(1.0); buf.putDouble(0.0); buf.putDouble(5.0);
        buf.putDouble(1.0); buf.putDouble(1.0); buf.putDouble(5.0);
        buf.putDouble(0.0); buf.putDouble(1.0); buf.putDouble(5.0);
        buf.putDouble(0.0); buf.putDouble(0.0); buf.putDouble(5.0);  // 闭合

        buf.put((byte) 0xFE);
        return buf.array();
    }

    /**
     * 构建头部字节不合规的字节数组。
     *
     * @param gaiaStart  offset 0 的字节值
     * @param byteOrder  offset 1 的字节值
     * @param gaiaMbrByte offset 38 的字节值（MBR 标记）
     * @param geoType    offset 39-42 的 geoType 值
     */
    private static byte[] buildBrokenHeader(byte gaiaStart, byte byteOrder,
                                             byte gaiaMbrByte, int geoType) {
        // 使用 2D MultiPolygon 的有效尺寸（141 bytes）
        ByteBuffer buf = ByteBuffer.allocate(141).order(ByteOrder.LITTLE_ENDIAN);

        buf.put(gaiaStart);      // offset 0: gaiaStart
        buf.put(byteOrder);      // offset 1: byteOrder
        buf.putInt(4326);        // offset 2-5: srid
        buf.putDouble(0.0);      // offset 6-13: minX
        buf.putDouble(0.0);      // offset 14-21: minY
        buf.putDouble(1.0);      // offset 22-29: maxX
        buf.putDouble(1.0);      // offset 30-37: maxY
        buf.put(gaiaMbrByte);    // offset 38: gaiaMBR marker
        buf.putInt(geoType);     // offset 39-42: geoType

        // 填充剩余字节（使 buf 合法，但不会被用到）
        buf.putInt(1);           // numPolygons = 1
        buf.put((byte) 0x69);
        buf.putInt(3);
        buf.putInt(1);
        buf.putInt(5);
        buf.putDouble(0.0); buf.putDouble(0.0);
        buf.putDouble(1.0); buf.putDouble(0.0);
        buf.putDouble(1.0); buf.putDouble(1.0);
        buf.putDouble(0.0); buf.putDouble(1.0);
        buf.putDouble(0.0); buf.putDouble(0.0);
        buf.put((byte) 0xFE);

        return buf.array();
    }

    // ── 测试方法 ──────────────────────────────────────────────────────────────

    @Test
    void readMultiPolygonAuto_geoType_2d_should_decode_correctly() {
        byte[] blob = buildMultiPolygon2D();

        MultiPolygon result = GaiaGeometryReader.readMultiPolygonAuto(blob);

        assertThat(result).isNotNull();
        assertThat(result.getNumGeometries()).isEqualTo(1);

        Polygon poly = (Polygon) result.getGeometryN(0);
        assertThat(poly.getExteriorRing().getCoordinates()).hasSize(5);
        // 验证外环第一个坐标
        assertThat(poly.getExteriorRing().getCoordinates()[0].x).isEqualTo(0.0);
        assertThat(poly.getExteriorRing().getCoordinates()[0].y).isEqualTo(0.0);
    }

    @Test
    void readMultiPolygonAuto_geoType_2d_should_have_no_z_coordinate() {
        byte[] blob = buildMultiPolygon2D();

        MultiPolygon result = GaiaGeometryReader.readMultiPolygonAuto(blob);

        Polygon poly = (Polygon) result.getGeometryN(0);
        double z = poly.getExteriorRing().getCoordinates()[0].getZ();
        // 2D 数据的 Z 应为 NaN
        assertThat(Double.isNaN(z)).isTrue();
    }

    @Test
    void readMultiPolygonAuto_geoType_3d_should_decode_correctly() {
        byte[] blob = buildMultiPolygonZ();

        MultiPolygon result = GaiaGeometryReader.readMultiPolygonAuto(blob);

        assertThat(result).isNotNull();
        assertThat(result.getNumGeometries()).isEqualTo(1);

        Polygon poly = (Polygon) result.getGeometryN(0);
        assertThat(poly.getExteriorRing().getCoordinates()).hasSize(5);
        // 验证 Z 坐标
        double z = poly.getExteriorRing().getCoordinates()[0].getZ();
        assertThat(z).isEqualTo(5.0);
    }

    @Test
    void readMultiPolygonAuto_geoType_3d_should_have_correct_xy() {
        byte[] blob = buildMultiPolygonZ();

        MultiPolygon result = GaiaGeometryReader.readMultiPolygonAuto(blob);

        Polygon poly = (Polygon) result.getGeometryN(0);
        assertThat(poly.getExteriorRing().getCoordinates()[0].x).isEqualTo(0.0);
        assertThat(poly.getExteriorRing().getCoordinates()[0].y).isEqualTo(0.0);
    }

    @Test
    void readMultiPolygonAuto_invalid_geoType_should_throw_IllegalArgumentException() {
        // 构建 geoType=5（MultiLineString），对 readMultiPolygonAuto 来说是非法类型
        byte[] blob = buildBrokenHeader((byte) 0x00, (byte) 0x01, (byte) 0x7c, 5);

        assertThatThrownBy(() -> GaiaGeometryReader.readMultiPolygonAuto(blob))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("期望 geoType=6 或 1006");
    }

    @Test
    void readMultiPolygonAuto_geoType_1_should_throw_IllegalArgumentException() {
        // geoType=1（Point）也是非法的
        byte[] blob = buildBrokenHeader((byte) 0x00, (byte) 0x01, (byte) 0x7c, 1);

        assertThatThrownBy(() -> GaiaGeometryReader.readMultiPolygonAuto(blob))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("期望 geoType=6 或 1006");
    }

    @Test
    void readMultiPolygonAuto_invalid_gaiaStart_should_throw_IllegalArgumentException() {
        // gaiaStart = 0xFF（非法）
        byte[] blob = buildBrokenHeader((byte) 0xFF, (byte) 0x01, (byte) 0x7c, 6);

        assertThatThrownBy(() -> GaiaGeometryReader.readMultiPolygonAuto(blob))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0x00");
    }

    @Test
    void readMultiPolygonAuto_invalid_byteOrder_should_throw_IllegalArgumentException() {
        // byteOrder = 0x00（Big-Endian，不支持）
        byte[] blob = buildBrokenHeader((byte) 0x00, (byte) 0x00, (byte) 0x7c, 6);

        assertThatThrownBy(() -> GaiaGeometryReader.readMultiPolygonAuto(blob))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0x01");
    }

    @Test
    void readMultiPolygonAuto_invalid_gaiaMBR_should_throw_IllegalArgumentException() {
        // gaiaMBR = 0xFF（非法）
        byte[] blob = buildBrokenHeader((byte) 0x00, (byte) 0x01, (byte) 0xFF, 6);

        assertThatThrownBy(() -> GaiaGeometryReader.readMultiPolygonAuto(blob))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0x7c");
    }

    @Test
    void readMultiPolygonAuto_geoType_2d_srid_should_be_set() {
        byte[] blob = buildMultiPolygon2D();

        MultiPolygon result = GaiaGeometryReader.readMultiPolygonAuto(blob);

        assertThat(result.getSRID()).isEqualTo(4326);
    }

    @Test
    void readMultiPolygonAuto_geoType_3d_srid_should_be_set() {
        byte[] blob = buildMultiPolygonZ();

        MultiPolygon result = GaiaGeometryReader.readMultiPolygonAuto(blob);

        assertThat(result.getSRID()).isEqualTo(4326);
    }
}
