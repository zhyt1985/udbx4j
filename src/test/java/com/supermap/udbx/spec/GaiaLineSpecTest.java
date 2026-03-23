package com.supermap.udbx.spec;

import com.supermap.udbx.geometry.gaia.GaiaGeometryReader;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.MultiLineString;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 测试：验证 GAIA MultiLineString / MultiLineStringZ 解码行为。
 *
 * <p>对应白皮书 §4.2.3（MultiLineString）和 §4.2.5（MultiLineStringZ）。
 *
 * <p>GAIA 二进制格式：
 * <pre>
 * 0x00 | byteOrder(1) | srid(int32) | MBR(4×double) | 0x7c | geoType(int32) | ...数据... | 0xFE
 * </pre>
 *
 * <p>MultiLineString (geoType=5) 数据部分：
 * <pre>
 * numLineStrings(int32)
 * foreach LineString:
 *   numPoints(int32)
 *   x1,y1, x2,y2, ... (double pairs)
 * </pre>
 *
 * <p>MultiLineStringZ (geoType=1005) 数据部分：
 * <pre>
 * numLineStrings(int32)
 * foreach LineString:
 *   numPoints(int32)
 *   x1,y1,z1, x2,y2,z2, ... (double triples)
 * </pre>
 *
 * <p>测试数据说明：
 * <ul>
 *   <li>srid = 4326（WGS84）</li>
 *   <li>2D: 2条线段：Line 0: (0,0)→(1,0)，Line 1: (0,1)→(2,1)</li>
 *   <li>3D: 1条线段，2个点: (1,2,3)→(4,5,6)</li>
 * </ul>
 */
class GaiaLineSpecTest {

    /**
     * 构建 GAIA MultiLineString 2D 测试字节数组。
     *
     * <p>内容：srid=4326，2条线段：
     * <ul>
     *   <li>Line 0: (0.0,0.0) → (1.0,0.0)</li>
     *   <li>Line 1: (0.0,1.0) → (2.0,1.0)</li>
     * </ul>
     *
     * <p>字节布局：
     * <ul>
     *   <li>头部：1+1+4+32+1+4 = 43 bytes</li>
     *   <li>numLines(4) + 2*[entityMark(1)+geoType(4)+numPts(4)+2*16] = 4+2*41 = 86 bytes</li>
     *   <li>尾部：1 byte</li>
     *   <li>合计：130 bytes</li>
     * </ul>
     */
    private static byte[] buildMultiLineString2D() {
        ByteBuffer buf = ByteBuffer.allocate(130).order(ByteOrder.LITTLE_ENDIAN);

        // GAIA 头部
        buf.put((byte) 0x00);           // gaiaStart
        buf.put((byte) 0x01);           // byteOrder = little-endian
        buf.putInt(4326);               // srid
        buf.putDouble(0.0);             // MBR minX
        buf.putDouble(0.0);             // MBR minY
        buf.putDouble(2.0);             // MBR maxX
        buf.putDouble(1.0);             // MBR maxY
        buf.put((byte) 0x7c);           // gaiaMBR marker
        buf.putInt(5);                  // geoType = MultiLineString

        // 数据：2条线段
        buf.putInt(2);                  // numLineStrings

        // Line 0: (0,0)→(1,0)
        buf.put((byte) 0x69);           // gaiaEntityMark
        buf.putInt(2);                  // LineString geoType
        buf.putInt(2);                  // numPoints
        buf.putDouble(0.0); buf.putDouble(0.0);
        buf.putDouble(1.0); buf.putDouble(0.0);

        // Line 1: (0,1)→(2,1)
        buf.put((byte) 0x69);           // gaiaEntityMark
        buf.putInt(2);                  // LineString geoType
        buf.putInt(2);                  // numPoints
        buf.putDouble(0.0); buf.putDouble(1.0);
        buf.putDouble(2.0); buf.putDouble(1.0);

        buf.put((byte) 0xFE);           // gaiaEnd
        return buf.array();
    }

    /**
     * 构建 GAIA MultiLineStringZ 3D 测试字节数组。
     *
     * <p>内容：srid=4326，1条线段：(1,2,3)→(4,5,6)。
     *
     * <p>字节布局：
     * <ul>
     *   <li>头部：43 bytes</li>
     *   <li>numLines(4) + [entityMark(1)+geoType(4)+numPts(4) + 2*24] = 4+57 = 61 bytes</li>
     *   <li>尾部：1 byte</li>
     *   <li>合计：105 bytes</li>
     * </ul>
     */
    private static byte[] buildMultiLineStringZ() {
        ByteBuffer buf = ByteBuffer.allocate(105).order(ByteOrder.LITTLE_ENDIAN);

        buf.put((byte) 0x00);
        buf.put((byte) 0x01);
        buf.putInt(4326);
        buf.putDouble(1.0); buf.putDouble(2.0);
        buf.putDouble(4.0); buf.putDouble(5.0);
        buf.put((byte) 0x7c);
        buf.putInt(1005);               // geoType = MultiLineStringZ

        buf.putInt(1);                  // numLineStrings = 1

        // Line 0: (1,2,3)→(4,5,6)
        buf.put((byte) 0x69);           // gaiaEntityMark
        buf.putInt(1002);               // LineStringZ geoType
        buf.putInt(2);                  // numPoints
        buf.putDouble(1.0); buf.putDouble(2.0); buf.putDouble(3.0);
        buf.putDouble(4.0); buf.putDouble(5.0); buf.putDouble(6.0);

        buf.put((byte) 0xFE);
        return buf.array();
    }

    // ── 测试方法 ──────────────────────────────────────────────────────────────

    @Test
    void gaia_multilinestring_2d_must_decode_correctly() {
        byte[] blob = buildMultiLineString2D();

        MultiLineString result = GaiaGeometryReader.readMultiLineString(blob);

        assertThat(result).isNotNull();
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    void gaia_multilinestring_2d_must_return_correct_num_geometries() {
        byte[] blob = buildMultiLineString2D();

        MultiLineString result = GaiaGeometryReader.readMultiLineString(blob);

        assertThat(result.getNumGeometries()).isEqualTo(2);
        // 验证第一条线的起点坐标
        assertThat(result.getGeometryN(0).getCoordinates()[0].x).isEqualTo(0.0);
        assertThat(result.getGeometryN(0).getCoordinates()[0].y).isEqualTo(0.0);
        assertThat(result.getGeometryN(0).getCoordinates()[1].x).isEqualTo(1.0);
        // 验证第二条线的终点坐标
        assertThat(result.getGeometryN(1).getCoordinates()[1].x).isEqualTo(2.0);
        assertThat(result.getGeometryN(1).getCoordinates()[1].y).isEqualTo(1.0);
    }

    @Test
    void gaia_multilinestring_z_must_decode_correctly() {
        byte[] blob = buildMultiLineStringZ();

        MultiLineString result = GaiaGeometryReader.readMultiLineStringZ(blob);

        assertThat(result).isNotNull();
        assertThat(result.getNumGeometries()).isEqualTo(1);
        // 验证 Z 坐标
        double z0 = result.getGeometryN(0).getCoordinates()[0].getZ();
        double z1 = result.getGeometryN(0).getCoordinates()[1].getZ();
        assertThat(z0).isEqualTo(3.0);
        assertThat(z1).isEqualTo(6.0);
    }
}
