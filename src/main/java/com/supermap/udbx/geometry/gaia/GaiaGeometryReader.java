package com.supermap.udbx.geometry.gaia;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import com.supermap.udbx.geometry.GeometryFactoryPool;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * GAIA Geometry 解码器。
 *
 * <p>解码 SpatiaLite GAIA 二进制格式（白皮书 §4.2）的几何对象。
 *
 * <p>GAIA 通用格式头：
 * <pre>
 * offset  size  description
 *      0     1  gaiaStart    = 0x00
 *      1     1  byteOrder    = 0x01 (Little-Endian)
 *      2     4  srid         int32
 *      6    32  mbr          4 × double (minX, minY, maxX, maxY)
 *     38     1  gaiaMBR      = 0x7c
 *     39     4  geoType      int32 (1=Point, 1001=PointZ, 5=MultiLineString, ...)
 *     43     ?  geometry data
 *      ?     1  gaiaEnd      = 0xFE
 * </pre>
 */
public final class GaiaGeometryReader {

    /** GAIA 格式中几何体数据起始偏移量（gaiaStart + byteOrder + srid + mbr + gaiaMBR + geoType）。 */
    private static final int GEOMETRY_DATA_OFFSET = 43;

    private static final byte GAIA_START = 0x00;
    private static final byte GAIA_MBR   = 0x7c;
    private static final byte GAIA_END   = (byte) 0xFE;

    private static final int GEO_TYPE_POINT             = 1;
    private static final int GEO_TYPE_POINTZ            = 1001;
    private static final int GEO_TYPE_MULTILINESTRING   = 5;
    private static final int GEO_TYPE_MULTILINESTRINGZ  = 1005;
    private static final int GEO_TYPE_MULTIPOLYGON      = 6;
    private static final int GEO_TYPE_MULTIPOLYGONZ     = 1006;

    // -----------------------------------------------------------------------
    // Point 解码（白皮书 §4.2.1）
    // -----------------------------------------------------------------------

    /**
     * 解码 GAIA Point（2D）字节序列，返回 JTS {@link Point}。
     *
     * <p>对应白皮书 §4.2.1，geoType=1。
     *
     * @param bytes GAIA 格式的字节数组（Little-Endian）
     * @return 包含 x、y 坐标和 SRID 的 JTS Point
     * @throws IllegalArgumentException 若字节数组格式不合法
     */
    public static Point readPoint(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        validateHeader(buf, GEO_TYPE_POINT);

        int srid = readSrid(buf);

        buf.position(GEOMETRY_DATA_OFFSET);
        double x = buf.getDouble();
        double y = buf.getDouble();

        validateGaiaEnd(buf);

        GeometryFactory factory = GeometryFactoryPool.getFactory(srid);
        return factory.createPoint(new Coordinate(x, y));
    }

    /**
     * 解码 GAIA PointZ（3D）字节序列，返回 JTS {@link Point}。
     *
     * <p>对应白皮书 §4.2.1，geoType=1001。
     *
     * @param bytes GAIA 格式的字节数组（Little-Endian）
     * @return 包含 x、y、z 坐标和 SRID 的 JTS Point
     * @throws IllegalArgumentException 若字节数组格式不合法
     */
    public static Point readPointZ(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        validateHeader(buf, GEO_TYPE_POINTZ);

        int srid = readSrid(buf);

        buf.position(GEOMETRY_DATA_OFFSET);
        double x = buf.getDouble();
        double y = buf.getDouble();
        double z = buf.getDouble();

        validateGaiaEnd(buf);

        GeometryFactory factory = GeometryFactoryPool.getFactory(srid);
        return factory.createPoint(new Coordinate(x, y, z));
    }

    // -----------------------------------------------------------------------
    // MultiLineString 解码（白皮书 §4.2.3 / §4.2.5）
    // -----------------------------------------------------------------------

    /**
     * 解码 GAIA MultiLineString（2D）字节序列，返回 JTS {@link MultiLineString}。
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
     * @param bytes GAIA 格式的字节数组（Little-Endian）
     * @return JTS MultiLineString
     * @throws IllegalArgumentException 若字节数组格式不合法
     */
    public static MultiLineString readMultiLineString(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        validateHeader(buf, GEO_TYPE_MULTILINESTRING);

        int srid = readSrid(buf);
        buf.position(GEOMETRY_DATA_OFFSET);

        int numLineStrings = buf.getInt();
        LineString[] lineStrings = new LineString[numLineStrings];

        GeometryFactory factory = GeometryFactoryPool.getFactory(srid);

        for (int i = 0; i < numLineStrings; i++) {
            // LineStringEntity: gaiaEntityMark(0x69) + geoType(2) + numPoints + coords
            byte entityMark = buf.get();
            if (entityMark != 0x69) {
                throw new IllegalArgumentException(
                    "Invalid LineStringEntity mark: expected 0x69, got 0x" + Integer.toHexString(entityMark & 0xFF));
            }
            int lineGeoType = buf.getInt();
            if (lineGeoType != 2) {
                throw new IllegalArgumentException(
                    "Invalid LineString geoType: expected 2, got " + lineGeoType);
            }
            int numPoints = buf.getInt();
            Coordinate[] coords = new Coordinate[numPoints];
            for (int j = 0; j < numPoints; j++) {
                double x = buf.getDouble();
                double y = buf.getDouble();
                coords[j] = new Coordinate(x, y);
            }
            lineStrings[i] = factory.createLineString(coords);
        }

        validateGaiaEnd(buf);

        return factory.createMultiLineString(lineStrings);
    }

    /**
     * 解码 GAIA MultiLineStringZ（3D）字节序列，返回 JTS {@link MultiLineString}。
     *
     * <p>对应白皮书 §4.2.5，geoType=1005。
     *
     * <p>数据部分结构：
     * <pre>
     * numLineStrings(int32)
     * foreach LineString:
     *   numPoints(int32)
     *   x1,y1,z1, x2,y2,z2, ... (double triples)
     * </pre>
     *
     * @param bytes GAIA 格式的字节数组（Little-Endian）
     * @return JTS MultiLineString（含 Z 坐标）
     * @throws IllegalArgumentException 若字节数组格式不合法
     */
    public static MultiLineString readMultiLineStringZ(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        validateHeader(buf, GEO_TYPE_MULTILINESTRINGZ);

        int srid = readSrid(buf);
        buf.position(GEOMETRY_DATA_OFFSET);

        int numLineStrings = buf.getInt();
        LineString[] lineStrings = new LineString[numLineStrings];

        GeometryFactory factory = GeometryFactoryPool.getFactory(srid);

        for (int i = 0; i < numLineStrings; i++) {
            // LineStringZEntity: gaiaEntityMark(0x69) + geoType(1002) + numPoints + coords
            byte entityMark = buf.get();
            if (entityMark != 0x69) {
                throw new IllegalArgumentException(
                    "Invalid LineStringZEntity mark: expected 0x69, got 0x" + Integer.toHexString(entityMark & 0xFF));
            }
            int lineGeoType = buf.getInt();
            if (lineGeoType != 1002) {
                throw new IllegalArgumentException(
                    "Invalid LineStringZ geoType: expected 1002, got " + lineGeoType);
            }
            int numPoints = buf.getInt();
            Coordinate[] coords = new Coordinate[numPoints];
            for (int j = 0; j < numPoints; j++) {
                double x = buf.getDouble();
                double y = buf.getDouble();
                double z = buf.getDouble();
                coords[j] = new Coordinate(x, y, z);
            }
            lineStrings[i] = factory.createLineString(coords);
        }

        validateGaiaEnd(buf);

        return factory.createMultiLineString(lineStrings);
    }

    // -----------------------------------------------------------------------
    // 自动检测 2D/3D 的通用读取方法
    // -----------------------------------------------------------------------

    /**
     * 自动检测 geoType 并读取 GAIA MultiPolygon（2D 或 3D）。
     *
     * @param bytes GAIA 格式字节数组
     * @return JTS MultiPolygon
     * @throws IllegalArgumentException 若格式不合法
     */
    public static MultiPolygon readMultiPolygonAuto(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        validateGaiaStart(buf);
        validateByteOrder(buf);
        skipSridAndMbr(buf);
        validateGaiaMbr(buf);

        int geoType = buf.getInt();

        if (geoType == GEO_TYPE_MULTIPOLYGON) {
            return readMultiPolygon(bytes);
        } else if (geoType == GEO_TYPE_MULTIPOLYGONZ) {
            return readMultiPolygonZ(bytes);
        } else {
            throw new IllegalArgumentException(
                    "期望 geoType=6 或 1006，实际得到: " + geoType);
        }
    }

    private static void validateGaiaStart(ByteBuffer buf) {
        if (buf.get() != GAIA_START) {
            throw new IllegalArgumentException("无效的 GAIA 起始标记，期望 0x00");
        }
    }

    private static void validateByteOrder(ByteBuffer buf) {
        byte order = buf.get();
        if (order != 0x01) {
            throw new IllegalArgumentException("不支持的 ByteOrder: " + order + "，期望 0x01 (Little-Endian)");
        }
    }

    private static void skipSridAndMbr(ByteBuffer buf) {
        buf.position(buf.position() + 4 + 32); // skip srid (4) + mbr (32)
    }

    private static void validateGaiaMbr(ByteBuffer buf) {
        if (buf.get() != GAIA_MBR) {
            throw new IllegalArgumentException("无效的 GAIA MBR 标记，期望 0x7c");
        }
    }

    // -----------------------------------------------------------------------
    // MultiPolygon 解码（白皮书 §4.2.5 / §4.2.7）
    // -----------------------------------------------------------------------

    /**
     * 解码 GAIA MultiPolygon（2D）字节序列，返回 JTS {@link MultiPolygon}。
     *
     * <p>对应白皮书 §4.2.5，geoType=6。
     *
     * <p>数据部分结构：
     * <pre>
     * numPolygons(int32)
     * foreach Polygon:
     *   entityMark(0x69)
     *   geoType(3)
     *   numInteriors(int32)
     *   exteriorRing: numPoints(int32) + x,y pairs
     *   foreach interiorRing: numPoints(int32) + x,y pairs
     * </pre>
     *
     * @param bytes GAIA 格式的字节数组（Little-Endian）
     * @return JTS MultiPolygon
     * @throws IllegalArgumentException 若字节数组格式不合法
     */
    public static MultiPolygon readMultiPolygon(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        validateHeader(buf, GEO_TYPE_MULTIPOLYGON);

        int srid = readSrid(buf);
        buf.position(GEOMETRY_DATA_OFFSET);

        int numPolygons = buf.getInt();
        Polygon[] polygons = new Polygon[numPolygons];

        GeometryFactory factory = GeometryFactoryPool.getFactory(srid);

        for (int i = 0; i < numPolygons; i++) {
            polygons[i] = readPolygon2D(buf, factory);
        }

        validateGaiaEnd(buf);

        return factory.createMultiPolygon(polygons);
    }

    /**
     * 解码 GAIA MultiPolygonZ（3D）字节序列，返回 JTS {@link MultiPolygon}。
     *
     * <p>对应白皮书 §4.2.7，geoType=1006。
     *
     * <p>数据部分结构：
     * <pre>
     * numPolygons(int32)
     * foreach Polygon:
     *   numRings(int32)
     *   foreach Ring:
     *     numPoints(int32)
     *     x1,y1,z1, ..., xN,yN,zN (double triples，闭合)
     * </pre>
     *
     * <p>第 0 个 Ring 为外环（shell），其余为内环（holes）。
     *
     * @param bytes GAIA 格式的字节数组（Little-Endian）
     * @return JTS MultiPolygon（含 Z 坐标）
     * @throws IllegalArgumentException 若字节数组格式不合法
     */
    public static MultiPolygon readMultiPolygonZ(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        validateHeader(buf, GEO_TYPE_MULTIPOLYGONZ);

        int srid = readSrid(buf);
        buf.position(GEOMETRY_DATA_OFFSET);

        int numPolygons = buf.getInt();
        Polygon[] polygons = new Polygon[numPolygons];

        GeometryFactory factory = GeometryFactoryPool.getFactory(srid);

        for (int i = 0; i < numPolygons; i++) {
            polygons[i] = readPolygon3D(buf, factory);
        }

        validateGaiaEnd(buf);

        return factory.createMultiPolygon(polygons);
    }

    // -----------------------------------------------------------------------
    // 私有辅助方法
    // -----------------------------------------------------------------------

    /**
     * 从 ByteBuffer 读取一个 2D Polygon（独立格式，含 entityMark）。
     *
     * <p>格式：gaiaEntityMark(0x69) + PolygonData
     * <p>PolygonData: geoType(3) + numInteriors + exteriorRing + interiorRings
     * <p>第 0 个 Ring 为外环，其余为内环。
     */
    private static Polygon readPolygon2D(ByteBuffer buf, GeometryFactory factory) {
        // PolygonEntity: gaiaEntityMark(0x69)
        byte entityMark = buf.get();
        if (entityMark != 0x69) {
            throw new IllegalArgumentException(
                "Invalid PolygonEntity mark: expected 0x69, got 0x" + Integer.toHexString(entityMark & 0xFF));
        }

        // PolygonData
        int geoType = buf.getInt();
        if (geoType != 3) {
            throw new IllegalArgumentException(
                "Invalid Polygon geoType: expected 3, got " + geoType);
        }

        int numRings = buf.getInt();  // total rings (1 exterior + N interior)
        if (numRings < 1) {
            throw new IllegalArgumentException("Polygon 必须至少有一个 Ring，实际: " + numRings);
        }

        LinearRing shell = readLinearRing2D(buf, factory);

        LinearRing[] holes = new LinearRing[numRings - 1];
        for (int r = 0; r < numRings - 1; r++) {
            holes[r] = readLinearRing2D(buf, factory);
        }

        return factory.createPolygon(shell, holes);
    }

    /**
     * 从 ByteBuffer 读取一个 3D Polygon。
     *
     * <p>格式：gaiaEntityMark(0x69) + PolygonZData
     * <p>PolygonZData: geoType(1003) + numInteriors + exteriorRing + interiorRings
     * <p>第 0 个 Ring 为外环，其余为内环。
     */
    private static Polygon readPolygon3D(ByteBuffer buf, GeometryFactory factory) {
        // PolygonEntity: gaiaEntityMark(0x69)
        byte entityMark = buf.get();
        if (entityMark != 0x69) {
            throw new IllegalArgumentException(
                "Invalid PolygonEntity mark: expected 0x69, got 0x" + Integer.toHexString(entityMark & 0xFF));
        }

        // PolygonZData
        int geoType = buf.getInt();
        if (geoType != 1003) {
            throw new IllegalArgumentException(
                "Invalid PolygonZ geoType: expected 1003, got " + geoType);
        }

        int numRings = buf.getInt();  // total rings (1 exterior + N interior)
        if (numRings < 1) {
            throw new IllegalArgumentException("Polygon 必须至少有一个 Ring，实际: " + numRings);
        }

        LinearRing shell = readLinearRing3D(buf, factory);

        LinearRing[] holes = new LinearRing[numRings - 1];
        for (int r = 0; r < numRings - 1; r++) {
            holes[r] = readLinearRing3D(buf, factory);
        }

        return factory.createPolygon(shell, holes);
    }

    /**
     * 从 ByteBuffer 读取一个 2D LinearRing。
     *
     * <p>格式：numPoints(int32) + x,y pairs（首尾重复，已闭合）。
     */
    private static LinearRing readLinearRing2D(ByteBuffer buf, GeometryFactory factory) {
        int numPoints = buf.getInt();
        Coordinate[] coords = new Coordinate[numPoints];
        for (int j = 0; j < numPoints; j++) {
            double x = buf.getDouble();
            double y = buf.getDouble();
            coords[j] = new Coordinate(x, y);
        }
        return factory.createLinearRing(coords);
    }

    /**
     * 从 ByteBuffer 读取一个 3D LinearRing。
     *
     * <p>格式：numPoints(int32) + x,y,z triples（首尾重复，已闭合）。
     */
    private static LinearRing readLinearRing3D(ByteBuffer buf, GeometryFactory factory) {
        int numPoints = buf.getInt();
        Coordinate[] coords = new Coordinate[numPoints];
        for (int j = 0; j < numPoints; j++) {
            double x = buf.getDouble();
            double y = buf.getDouble();
            double z = buf.getDouble();
            coords[j] = new Coordinate(x, y, z);
        }
        return factory.createLinearRing(coords);
    }

    /**
     * 读取并验证 GAIA 格式头，确认 gaiaStart、byteOrder、gaiaMBR 标志字节及 geoType 符合规范。
     *
     * @param buf             已设置为 LITTLE_ENDIAN 的 ByteBuffer
     * @param expectedGeoType 期望的 geoType 值
     * @throws IllegalArgumentException 若头部字节不合规范
     */
    private static void validateHeader(ByteBuffer buf, int expectedGeoType) {
        byte gaiaStart = buf.get();       // offset 0
        if (gaiaStart != GAIA_START) {
            throw new IllegalArgumentException(
                    "无效的 GAIA 格式：gaiaStart 应为 0x00，实际为 0x"
                            + Integer.toHexString(gaiaStart & 0xFF));
        }

        byte byteOrder = buf.get();       // offset 1
        if (byteOrder != 0x01) {
            throw new IllegalArgumentException(
                    "不支持的字节序：byteOrder 应为 0x01（Little-Endian），实际为 0x"
                            + Integer.toHexString(byteOrder & 0xFF));
        }

        // offset 2-5: srid（由 readSrid 绝对读取，此处跳过）
        buf.position(buf.position() + 4);

        // offset 6-37: mbr（4 × double = 32 bytes）
        buf.position(buf.position() + 32);

        byte gaiaMBR = buf.get();         // offset 38
        if (gaiaMBR != GAIA_MBR) {
            throw new IllegalArgumentException(
                    "无效的 GAIA 格式：gaiaMBR 应为 0x7c，实际为 0x"
                            + Integer.toHexString(gaiaMBR & 0xFF));
        }

        int geoType = buf.getInt();       // offset 39-42
        if (geoType != expectedGeoType) {
            throw new IllegalArgumentException(
                    "geoType 不匹配：期望 " + expectedGeoType + "，实际为 " + geoType);
        }
    }

    /**
     * 从 offset 2 绝对读取 srid（int32，Little-Endian），不移动 ByteBuffer position。
     */
    private static int readSrid(ByteBuffer buf) {
        return buf.getInt(2);
    }

    /**
     * 验证 GAIA 结束字节 0xFE（gaiaEnd）。
     *
     * @throws IllegalArgumentException 若结束字节不为 0xFE
     */
    private static void validateGaiaEnd(ByteBuffer buf) {
        byte gaiaEnd = buf.get();
        if (gaiaEnd != GAIA_END) {
            throw new IllegalArgumentException(
                    "无效的 GAIA 格式：gaiaEnd 应为 0xFE，实际为 0x"
                            + Integer.toHexString(gaiaEnd & 0xFF));
        }
    }
}
