package com.supermap.udbx.spec;

import com.supermap.udbx.geometry.gaia.GaiaGeometryReader;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Spec 测试：验证 GAIAPoint / GAIAPointZ 二进制格式解码。
 *
 * <p>对应白皮书章节：4.2.1 GAIAPoint
 *
 * <p>二进制结构（2D Point）：
 * <pre>
 * GAIAPoint {
 *     static byte  gaiaStart  = 0x00;
 *     byte         byteOrder  = 0x01;  // Little-Endian
 *     int32        srid;
 *     Rect         mbr;                // minX, minY, maxX, maxY（4 × double = 32 bytes）
 *     static byte  gaiaMBR    = 0x7c;
 *     static int32 geoType    = 1;     // GAIAPoint
 *     double       x;
 *     double       y;
 *     static byte  gaiaEnd    = 0xFE;
 * }
 * </pre>
 *
 * <p>二进制结构（3D PointZ）：
 * <pre>
 * GAIAPointZ {
 *     static byte  gaiaStart  = 0x00;
 *     byte         byteOrder  = 0x01;  // Little-Endian
 *     int32        srid;
 *     Rect         mbr;                // minX, minY, maxX, maxY（4 × double = 32 bytes）
 *     static byte  gaiaMBR    = 0x7c;
 *     static int32 geoType    = 1001;  // GAIAPointZ
 *     double       x;
 *     double       y;
 *     double       z;
 *     static byte  gaiaEnd    = 0xFE;
 * }
 * </pre>
 */
class GaiaPointSpecTest {

    /**
     * 构造 GAIAPoint（2D）字节序列：
     * gaiaStart(1) + byteOrder(1) + srid(4) + mbr(32) + gaiaMBR(1) + geoType(4) + x(8) + y(8) + gaiaEnd(1) = 60 bytes
     */
    private static byte[] buildGaiaPoint2D(double x, double y, int srid) {
        ByteBuffer buf = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0x00);       // gaiaStart
        buf.put((byte) 0x01);       // byteOrder = Little-Endian
        buf.putInt(srid);           // srid
        buf.putDouble(x);           // mbr.minX
        buf.putDouble(y);           // mbr.minY
        buf.putDouble(x);           // mbr.maxX
        buf.putDouble(y);           // mbr.maxY
        buf.put((byte) 0x7c);       // gaiaMBR
        buf.putInt(1);              // geoType = GAIAPoint
        buf.putDouble(x);           // x
        buf.putDouble(y);           // y
        buf.put((byte) 0xFE);       // gaiaEnd
        return buf.array();
    }

    /**
     * 构造 GAIAPointZ（3D）字节序列：
     * gaiaStart(1) + byteOrder(1) + srid(4) + mbr(32) + gaiaMBR(1) + geoType(4) + x(8) + y(8) + z(8) + gaiaEnd(1) = 68 bytes
     */
    private static byte[] buildGaiaPointZ(double x, double y, double z, int srid) {
        ByteBuffer buf = ByteBuffer.allocate(68).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0x00);       // gaiaStart
        buf.put((byte) 0x01);       // byteOrder = Little-Endian
        buf.putInt(srid);           // srid
        buf.putDouble(x);           // mbr.minX
        buf.putDouble(y);           // mbr.minY
        buf.putDouble(x);           // mbr.maxX
        buf.putDouble(y);           // mbr.maxY
        buf.put((byte) 0x7c);       // gaiaMBR
        buf.putInt(1001);           // geoType = GAIAPointZ
        buf.putDouble(x);           // x
        buf.putDouble(y);           // y
        buf.putDouble(z);           // z
        buf.put((byte) 0xFE);       // gaiaEnd
        return buf.array();
    }

    // -----------------------------------------------------------------------
    // 2D Point 结构验证
    // -----------------------------------------------------------------------

    /**
     * 验证 GAIAPoint 解码后返回非 null 的 JTS Point 对象，
     * 且 SRID 与输入一致（白皮书 4.2.1 结构完整性验证）。
     */
    @Test
    void gaia_point_2d_structure_must_conform_to_spec() {
        byte[] bytes = buildGaiaPoint2D(116.3912, 39.9075, 4326);

        Point point = GaiaGeometryReader.readPoint(bytes);

        assertThat(point).isNotNull();
        assertThat(point.getSRID()).isEqualTo(4326);
        assertThat(point.isEmpty()).isFalse();
    }

    /**
     * 验证 GAIAPoint 解码后 x、y 坐标精度（白皮书 4.2.1 坐标字段 double 精度）。
     *
     * <p>已知输入：x=116.3912（东经 116.3912°），y=39.9075（北纬 39.9075°），srid=4326
     */
    @Test
    void gaia_point_2d_must_return_correct_coordinates() {
        byte[] bytes = buildGaiaPoint2D(116.3912, 39.9075, 4326);

        Point point = GaiaGeometryReader.readPoint(bytes);

        assertThat(point.getX()).isCloseTo(116.3912, within(1e-10));
        assertThat(point.getY()).isCloseTo(39.9075, within(1e-10));
    }

    // -----------------------------------------------------------------------
    // 3D PointZ 结构验证
    // -----------------------------------------------------------------------

    /**
     * 验证 GAIAPointZ 解码后返回非 null 的 JTS Point 对象，
     * 且 SRID 与输入一致（白皮书 4.2.1 GAIAPointZ，geoType=1001）。
     */
    @Test
    void gaia_point_z_must_conform_to_spec() {
        byte[] bytes = buildGaiaPointZ(116.3912, 39.9075, 50.0, 4326);

        Point point = GaiaGeometryReader.readPointZ(bytes);

        assertThat(point).isNotNull();
        assertThat(point.getSRID()).isEqualTo(4326);
        assertThat(point.isEmpty()).isFalse();
    }

    /**
     * 验证 GAIAPointZ 解码后 x、y、z 坐标精度（白皮书 4.2.1 GAIAPointZ，geoType=1001）。
     *
     * <p>已知输入：x=116.3912，y=39.9075，z=50.0（海拔 50 米），srid=4326
     */
    @Test
    void gaia_point_z_must_return_correct_coordinates() {
        byte[] bytes = buildGaiaPointZ(116.3912, 39.9075, 50.0, 4326);

        Point point = GaiaGeometryReader.readPointZ(bytes);

        assertThat(point.getX()).isCloseTo(116.3912, within(1e-10));
        assertThat(point.getY()).isCloseTo(39.9075, within(1e-10));
        assertThat(point.getCoordinate().getZ()).isCloseTo(50.0, within(1e-10));
    }
}
