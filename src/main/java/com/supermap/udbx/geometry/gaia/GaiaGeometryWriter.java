package com.supermap.udbx.geometry.gaia;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * GAIA Geometry 编码器。
 *
 * <p>将 JTS Geometry 编码为 SpatiaLite GAIA 二进制格式（白皮书 §4.2）。
 *
 * <p>GAIA 通用格式头：
 * <pre>
 * offset  size  description
 *      0     1  gaiaStart    = 0x00
 *      1     1  byteOrder    = 0x01 (Little-Endian)
 *      2     4  srid         int32
 *      6    32  mbr          4 × double (minX, minY, maxX, maxY)
 *     38     1  gaiaMBR      = 0x7c
 *     39     4  geoType      int32
 *     43     ?  geometry data
 *      ?     1  gaiaEnd      = 0xFE
 * </pre>
 */
public final class GaiaGeometryWriter {

    private static final byte GAIA_START = 0x00;
    private static final byte BYTE_ORDER_LITTLE_ENDIAN = 0x01;
    private static final byte GAIA_MBR = 0x7c;
    private static final byte GAIA_END = (byte) 0xFE;

    private static final int GEO_TYPE_POINT = 1;
    private static final int GEO_TYPE_POINTZ = 1001;
    private static final int GEO_TYPE_MULTILINESTRING = 5;
    private static final int GEO_TYPE_MULTILINESTRINGZ = 1005;
    private static final int GEO_TYPE_MULTIPOLYGON = 6;
    private static final int GEO_TYPE_MULTIPOLYGONZ = 1006;

    // -----------------------------------------------------------------------
    // Point 编码（白皮书 §4.2.1）
    // -----------------------------------------------------------------------

    /**
     * 将 JTS Point（2D）编码为 GAIA 字节序列。
     *
     * <p>对应白皮书 §4.2.1，geoType=1。
     *
     * @param point  JTS Point（2D）
     * @param srid   坐标系 ID
     * @return GAIA 格式的字节数组
     */
    public static byte[] writePoint(Point point, int srid) {
        // header(43) + x(double) + y(double) + end(1) = 60
        ByteBuffer buf = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN);

        writeHeader(buf, srid, GEO_TYPE_POINT, point.getEnvelopeInternal());

        buf.putDouble(point.getX());
        buf.putDouble(point.getY());

        buf.put(GAIA_END);

        return buf.array();
    }

    /**
     * 将 JTS Point（3D）编码为 GAIA 字节序列。
     *
     * <p>对应白皮书 §4.2.1，geoType=1001。
     *
     * @param point  JTS Point（3D，含 Z 坐标）
     * @param srid   坐标系 ID
     * @return GAIA 格式的字节数组
     */
    public static byte[] writePointZ(Point point, int srid) {
        // header(43) + x(double) + y(double) + z(double) + end(1) = 68
        ByteBuffer buf = ByteBuffer.allocate(68).order(ByteOrder.LITTLE_ENDIAN);

        writeHeader(buf, srid, GEO_TYPE_POINTZ, point.getEnvelopeInternal());

        Coordinate coord = point.getCoordinate();
        buf.putDouble(coord.x);
        buf.putDouble(coord.y);
        buf.putDouble(coord.getZ());

        buf.put(GAIA_END);

        return buf.array();
    }

    // -----------------------------------------------------------------------
    // MultiLineString 编码（白皮书 §4.2.3）
    // -----------------------------------------------------------------------

    /**
     * 将 JTS MultiLineString（2D）编码为 GAIA 字节序列。
     *
     * <p>对应白皮书 §4.2.3，geoType=5。
     *
     * <p>数据部分结构：
     * <pre>
     * numLineStrings(int32)
     * foreach LineString:
     *   numPoints(int32)
     *   x1,y1, x2,y2, ... (double pairs)
     * </pre>
     *
     * @param multiLineString  JTS MultiLineString
     * @param srid             坐标系 ID
     * @return GAIA 格式的字节数组
     */
    public static byte[] writeMultiLineString(MultiLineString multiLineString, int srid) {
        int numLineStrings = multiLineString.getNumGeometries();
        int totalPoints = 0;
        for (int i = 0; i < numLineStrings; i++) {
            totalPoints += ((LineString) multiLineString.getGeometryN(i)).getNumPoints();
        }

        // header(43) + numLineStrings(int) + sum(entityMark(1) + geoType(4) + numPoints(4) + points * 2 * double) + end(1)
        int size = 43 + 4 + numLineStrings * (1 + 4 + 4) + totalPoints * 16 + 1;
        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);

        writeHeader(buf, srid, GEO_TYPE_MULTILINESTRING, multiLineString.getEnvelopeInternal());

        buf.putInt(numLineStrings);
        for (int i = 0; i < numLineStrings; i++) {
            LineString line = (LineString) multiLineString.getGeometryN(i);
            Coordinate[] coords = line.getCoordinates();
            buf.put((byte) 0x69); // gaiaEntityMark
            buf.putInt(2); // LineString geoType
            buf.putInt(coords.length);
            for (Coordinate coord : coords) {
                buf.putDouble(coord.x);
                buf.putDouble(coord.y);
            }
        }

        buf.put(GAIA_END);
        return buf.array();
    }

    /**
     * 将 JTS MultiLineString（3D）编码为 GAIA 字节序列。
     *
     * <p>对应白皮书 §4.2.5，geoType=1005。
     *
     * @param multiLineString  JTS MultiLineString（含 Z 坐标）
     * @param srid             坐标系 ID
     * @return GAIA 格式的字节数组
     */
    public static byte[] writeMultiLineStringZ(MultiLineString multiLineString, int srid) {
        int numLineStrings = multiLineString.getNumGeometries();
        int totalPoints = 0;
        for (int i = 0; i < numLineStrings; i++) {
            totalPoints += ((LineString) multiLineString.getGeometryN(i)).getNumPoints();
        }

        // header(43) + numLineStrings(int) + sum(entityMark(1) + geoType(4) + numPoints(4) + points * 3 * double) + end(1)
        int size = 43 + 4 + numLineStrings * (1 + 4 + 4) + totalPoints * 24 + 1;
        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);

        writeHeader(buf, srid, GEO_TYPE_MULTILINESTRINGZ, multiLineString.getEnvelopeInternal());

        buf.putInt(numLineStrings);
        for (int i = 0; i < numLineStrings; i++) {
            LineString line = (LineString) multiLineString.getGeometryN(i);
            Coordinate[] coords = line.getCoordinates();
            buf.put((byte) 0x69); // gaiaEntityMark
            buf.putInt(1002); // LineStringZ geoType
            buf.putInt(coords.length);
            for (Coordinate coord : coords) {
                buf.putDouble(coord.x);
                buf.putDouble(coord.y);
                buf.putDouble(coord.getZ());
            }
        }

        buf.put(GAIA_END);
        return buf.array();
    }

    // -----------------------------------------------------------------------
    // MultiPolygon 编码（白皮书 §4.2.5）
    // -----------------------------------------------------------------------

    /**
     * 将 JTS MultiPolygon（2D）编码为 GAIA 字节序列。
     *
     * <p>对应白皮书 §4.2.5，geoType=6。
     *
     * <p>数据部分结构：
     * <pre>
     * numPolygons(int32)
     * foreach Polygon:
     *   numRings(int32)
     *   foreach Ring:
     *     numPoints(int32)
     *     x1,y1, ..., xN,yN (double pairs，闭合，首尾重复)
     * </pre>
     *
     * @param multiPolygon  JTS MultiPolygon
     * @param srid          坐标系 ID
     * @return GAIA 格式的字节数组
     */
    public static byte[] writeMultiPolygon(MultiPolygon multiPolygon, int srid) {
        int numPolygons = multiPolygon.getNumGeometries();

        // 计算所需空间
        // header(43) + numPolygons(int) + sum(entityMark(1) + geoType(4) + numInteriors(4)) + rings + end(1)
        int totalSize = 43 + 4 + 1; // header + numPolygons + end
        for (int p = 0; p < numPolygons; p++) {
            Polygon poly = (Polygon) multiPolygon.getGeometryN(p);
            totalSize += 1 + 4 + 4; // entityMark + geoType + numInteriors
            totalSize += 4; // shell numPoints
            totalSize += poly.getExteriorRing().getNumPoints() * 16;
            for (int h = 0; h < poly.getNumInteriorRing(); h++) {
                totalSize += 4; // hole numPoints
                totalSize += poly.getInteriorRingN(h).getNumPoints() * 16;
            }
        }

        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        writeHeader(buf, srid, GEO_TYPE_MULTIPOLYGON, multiPolygon.getEnvelopeInternal());

        buf.putInt(numPolygons);
        for (int p = 0; p < numPolygons; p++) {
            Polygon poly = (Polygon) multiPolygon.getGeometryN(p);
            int numInteriors = poly.getNumInteriorRing();

            // PolygonEntity: gaiaEntityMark
            buf.put((byte) 0x69);
            // PolygonData: geoType = 3
            buf.putInt(3);
            // numRings (total: 1 exterior + numInteriors)
            buf.putInt(numInteriors + 1);

            // Shell (exterior ring)
            writeLinearRing2D(buf, poly.getExteriorRing());

            // Holes (interior rings)
            for (int h = 0; h < numInteriors; h++) {
                writeLinearRing2D(buf, poly.getInteriorRingN(h));
            }
        }

        buf.put(GAIA_END);
        return buf.array();
    }

    /**
     * 将 JTS MultiPolygon（3D）编码为 GAIA 字节序列。
     *
     * <p>对应白皮书 §4.2.7，geoType=1006。
     *
     * @param multiPolygon  JTS MultiPolygon（含 Z 坐标）
     * @param srid          坐标系 ID
     * @return GAIA 格式的字节数组
     */
    public static byte[] writeMultiPolygonZ(MultiPolygon multiPolygon, int srid) {
        int numPolygons = multiPolygon.getNumGeometries();

        // 计算所需空间（3D 每个点 24 字节）
        // header(43) + numPolygons(int) + sum(entityMark(1) + geoType(4) + numInteriors(4)) + rings + end(1)
        int totalSize = 43 + 4 + 1; // header + numPolygons + end
        for (int p = 0; p < numPolygons; p++) {
            Polygon poly = (Polygon) multiPolygon.getGeometryN(p);
            totalSize += 1 + 4 + 4; // entityMark + geoType + numInteriors
            totalSize += 4; // shell numPoints
            totalSize += poly.getExteriorRing().getNumPoints() * 24;
            for (int h = 0; h < poly.getNumInteriorRing(); h++) {
                totalSize += 4; // hole numPoints
                totalSize += poly.getInteriorRingN(h).getNumPoints() * 24;
            }
        }

        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        writeHeader(buf, srid, GEO_TYPE_MULTIPOLYGONZ, multiPolygon.getEnvelopeInternal());

        buf.putInt(numPolygons);
        for (int p = 0; p < numPolygons; p++) {
            Polygon poly = (Polygon) multiPolygon.getGeometryN(p);
            int numInteriors = poly.getNumInteriorRing();

            // PolygonEntity: gaiaEntityMark
            buf.put((byte) 0x69);
            // PolygonZData: geoType = 1003
            buf.putInt(1003);
            // numRings (total: 1 exterior + numInteriors)
            buf.putInt(numInteriors + 1);

            // Shell
            writeLinearRing3D(buf, poly.getExteriorRing());

            // Holes
            for (int h = 0; h < numInteriors; h++) {
                writeLinearRing3D(buf, poly.getInteriorRingN(h));
            }
        }

        buf.put(GAIA_END);
        return buf.array();
    }

    // -----------------------------------------------------------------------
    // 私有辅助方法
    // -----------------------------------------------------------------------

    /**
     * 写入 GAIA 格式头部（43 字节）。
     *
     * <p>格式：gaiaStart(1) + byteOrder(1) + srid(4) + mbr(32) + gaiaMBR(1) + geoType(4)
     */
    private static void writeHeader(ByteBuffer buf, int srid, int geoType, Envelope mbr) {
        buf.put(GAIA_START);
        buf.put(BYTE_ORDER_LITTLE_ENDIAN);
        buf.putInt(srid);
        buf.putDouble(mbr.getMinX());
        buf.putDouble(mbr.getMinY());
        buf.putDouble(mbr.getMaxX());
        buf.putDouble(mbr.getMaxY());
        buf.put(GAIA_MBR);
        buf.putInt(geoType);
    }

    /**
     * 写入 2D LinearRing。
     */
    private static void writeLinearRing2D(ByteBuffer buf, LineString ring) {
        Coordinate[] coords = ring.getCoordinates();
        buf.putInt(coords.length);
        for (Coordinate coord : coords) {
            buf.putDouble(coord.x);
            buf.putDouble(coord.y);
        }
    }

    /**
     * 写入 3D LinearRing。
     */
    private static void writeLinearRing3D(ByteBuffer buf, LineString ring) {
        Coordinate[] coords = ring.getCoordinates();
        buf.putInt(coords.length);
        for (Coordinate coord : coords) {
            buf.putDouble(coord.x);
            buf.putDouble(coord.y);
            buf.putDouble(coord.getZ());
        }
    }
}
