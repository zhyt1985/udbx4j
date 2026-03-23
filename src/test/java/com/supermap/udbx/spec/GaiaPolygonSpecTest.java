package com.supermap.udbx.spec;

import com.supermap.udbx.geometry.gaia.GaiaGeometryReader;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 测试：验证 GAIA MultiPolygon / MultiPolygonZ 解码行为。
 *
 * <p>对应白皮书 §4.2.5（MultiPolygon）和 §4.2.7（MultiPolygonZ）。
 *
 * <p>MultiPolygon (geoType=6) 数据部分：
 * <pre>
 * numPolygons(int32)
 * foreach Polygon:
 *   numRings(int32)
 *   foreach Ring:
 *     numPoints(int32)
 *     x1,y1, ..., xN,yN (double pairs，闭合，首尾重复)
 * </pre>
 *
 * <p>MultiPolygonZ (geoType=1006) 数据部分：
 * <pre>
 * numPolygons(int32)
 * foreach Polygon:
 *   numRings(int32)
 *   foreach Ring:
 *     numPoints(int32)
 *     x1,y1,z1, ..., xN,yN,zN (double triples，闭合)
 * </pre>
 *
 * <p>测试数据说明：
 * <ul>
 *   <li>2D 无洞: 1个多边形，外环5点（含闭合）: (0,0)→(1,0)→(1,1)→(0,1)→(0,0)</li>
 *   <li>带洞: 1个多边形，外环+内环，各5点（含闭合）</li>
 *   <li>3D: 1个多边形，外环5点（含闭合），Z=10.0</li>
 * </ul>
 */
class GaiaPolygonSpecTest {

    /**
     * 构建 GAIA MultiPolygon 2D 测试字节数组（无内环）。
     *
     * <p>内容：srid=4326，1个多边形，外环5点（含闭合）：
     * (0,0)→(1,0)→(1,1)→(0,1)→(0,0)
     *
     * <p>字节布局：
     * <ul>
     *   <li>头部：43 bytes</li>
     *   <li>numPolygons(4) + entityMark(1)+geoType(4)+numInteriors(4) + numPoints(4) + 5*2*8 = 4+9+4+80 = 97 bytes</li>
     *   <li>尾部：1 byte</li>
     *   <li>合计：141 bytes</li>
     * </ul>
     */
    private static byte[] buildMultiPolygon2D() {
        ByteBuffer buf = ByteBuffer.allocate(141).order(ByteOrder.LITTLE_ENDIAN);

        writeHeader(buf, 6, 0.0, 0.0, 1.0, 1.0);

        buf.putInt(1);          // numPolygons = 1
        buf.put((byte) 0x69);  // gaiaEntityMark
        buf.putInt(3);          // geoType = Polygon
        buf.putInt(1);          // numRings = 1 (exterior only, no holes)
        buf.putInt(5);          // shell numPoints = 5（含闭合）
        buf.putDouble(0.0); buf.putDouble(0.0);  // (0,0)
        buf.putDouble(1.0); buf.putDouble(0.0);  // (1,0)
        buf.putDouble(1.0); buf.putDouble(1.0);  // (1,1)
        buf.putDouble(0.0); buf.putDouble(1.0);  // (0,1)
        buf.putDouble(0.0); buf.putDouble(0.0);  // (0,0) 闭合

        buf.put((byte) 0xFE);
        return buf.array();
    }

    /**
     * 构建带内环（洞）的 GAIA MultiPolygon 2D 字节数组。
     *
     * <p>内容：srid=4326，1个多边形，2个环（外环+内环），各5点（含闭合）。
     *
     * <p>字节布局：
     * <ul>
     *   <li>头部：43 bytes</li>
     *   <li>numPolygons(4) + entityMark(1)+geoType(4)+numInteriors(4) + [numPts(4)+5*16] + [numPts(4)+5*16] = 4+9+84+84 = 181 bytes</li>
     *   <li>尾部：1 byte</li>
     *   <li>合计：225 bytes</li>
     * </ul>
     */
    private static byte[] buildMultiPolygon2DWithHole() {
        ByteBuffer buf = ByteBuffer.allocate(225).order(ByteOrder.LITTLE_ENDIAN);

        writeHeader(buf, 6, 0.0, 0.0, 1.0, 1.0);

        buf.putInt(1);          // numPolygons = 1
        buf.put((byte) 0x69);  // gaiaEntityMark
        buf.putInt(3);          // geoType = Polygon
        buf.putInt(2);          // numRings = 2 (1 exterior + 1 interior/hole)

        // 外环: (0,0)→(1,0)→(1,1)→(0,1)→(0,0)
        buf.putInt(5);
        buf.putDouble(0.0); buf.putDouble(0.0);
        buf.putDouble(1.0); buf.putDouble(0.0);
        buf.putDouble(1.0); buf.putDouble(1.0);
        buf.putDouble(0.0); buf.putDouble(1.0);
        buf.putDouble(0.0); buf.putDouble(0.0);

        // 内环（洞）: (0.2,0.2)→(0.8,0.2)→(0.8,0.8)→(0.2,0.8)→(0.2,0.2)
        buf.putInt(5);
        buf.putDouble(0.2); buf.putDouble(0.2);
        buf.putDouble(0.8); buf.putDouble(0.2);
        buf.putDouble(0.8); buf.putDouble(0.8);
        buf.putDouble(0.2); buf.putDouble(0.8);
        buf.putDouble(0.2); buf.putDouble(0.2);

        buf.put((byte) 0xFE);
        return buf.array();
    }

    /**
     * 构建 GAIA MultiPolygonZ 3D 测试字节数组。
     *
     * <p>内容：srid=4326，1个多边形，外环5点（含闭合），Z=10.0：
     * (0,0,10)→(1,0,10)→(1,1,10)→(0,1,10)→(0,0,10)
     *
     * <p>字节布局：
     * <ul>
     *   <li>头部：43 bytes</li>
     *   <li>numPolygons(4) + entityMark(1)+geoType(4)+numInteriors(4) + numPoints(4) + 5*3*8 = 4+9+4+120 = 137 bytes</li>
     *   <li>尾部：1 byte</li>
     *   <li>合计：181 bytes</li>
     * </ul>
     */
    private static byte[] buildMultiPolygonZ() {
        ByteBuffer buf = ByteBuffer.allocate(181).order(ByteOrder.LITTLE_ENDIAN);

        writeHeader(buf, 1006, 0.0, 0.0, 1.0, 1.0);

        buf.putInt(1);          // numPolygons = 1
        buf.put((byte) 0x69);  // gaiaEntityMark
        buf.putInt(1003);       // geoType = PolygonZ
        buf.putInt(1);          // numRings = 1 (exterior only)
        buf.putInt(5);          // shell numPoints = 5

        buf.putDouble(0.0); buf.putDouble(0.0); buf.putDouble(10.0);
        buf.putDouble(1.0); buf.putDouble(0.0); buf.putDouble(10.0);
        buf.putDouble(1.0); buf.putDouble(1.0); buf.putDouble(10.0);
        buf.putDouble(0.0); buf.putDouble(1.0); buf.putDouble(10.0);
        buf.putDouble(0.0); buf.putDouble(0.0); buf.putDouble(10.0);

        buf.put((byte) 0xFE);
        return buf.array();
    }

    /**
     * 写入 GAIA 头部（43 bytes）。
     *
     * @param buf     目标 ByteBuffer（已设置 LITTLE_ENDIAN 字节序）
     * @param geoType geometry 类型常量
     * @param minX    MBR 最小 X
     * @param minY    MBR 最小 Y
     * @param maxX    MBR 最大 X
     * @param maxY    MBR 最大 Y
     */
    private static void writeHeader(ByteBuffer buf, int geoType,
                                    double minX, double minY, double maxX, double maxY) {
        buf.put((byte) 0x00);           // gaiaStart
        buf.put((byte) 0x01);           // byteOrder = little-endian
        buf.putInt(4326);               // srid
        buf.putDouble(minX);
        buf.putDouble(minY);
        buf.putDouble(maxX);
        buf.putDouble(maxY);
        buf.put((byte) 0x7c);           // gaiaMBR marker
        buf.putInt(geoType);
    }

    // ── 测试方法 ──────────────────────────────────────────────────────────────

    @Test
    void gaia_multipolygon_2d_must_decode_correctly() {
        byte[] blob = buildMultiPolygon2D();

        MultiPolygon result = GaiaGeometryReader.readMultiPolygon(blob);

        assertThat(result).isNotNull();
        assertThat(result.isEmpty()).isFalse();
        assertThat(result.getNumGeometries()).isEqualTo(1);
    }

    @Test
    void gaia_multipolygon_2d_must_handle_holes() {
        byte[] blob = buildMultiPolygon2DWithHole();

        MultiPolygon result = GaiaGeometryReader.readMultiPolygon(blob);

        assertThat(result).isNotNull();
        assertThat(result.getNumGeometries()).isEqualTo(1);

        // 第一个多边形包含 1 个内环（洞）
        Polygon poly = (Polygon) result.getGeometryN(0);
        assertThat(poly.getNumInteriorRing()).isEqualTo(1);

        // 验证外环起点坐标
        assertThat(poly.getExteriorRing().getCoordinates()[0].x).isEqualTo(0.0);
        assertThat(poly.getExteriorRing().getCoordinates()[0].y).isEqualTo(0.0);

        // 验证内环起点坐标
        assertThat(poly.getInteriorRingN(0).getCoordinates()[0].x).isEqualTo(0.2);
        assertThat(poly.getInteriorRingN(0).getCoordinates()[0].y).isEqualTo(0.2);
    }

    @Test
    void gaia_multipolygon_z_must_decode_correctly() {
        byte[] blob = buildMultiPolygonZ();

        MultiPolygon result = GaiaGeometryReader.readMultiPolygonZ(blob);

        assertThat(result).isNotNull();
        assertThat(result.getNumGeometries()).isEqualTo(1);

        // 验证 Z 坐标
        Polygon poly = (Polygon) result.getGeometryN(0);
        double z = poly.getExteriorRing().getCoordinates()[0].getZ();
        assertThat(z).isEqualTo(10.0);
    }
}
