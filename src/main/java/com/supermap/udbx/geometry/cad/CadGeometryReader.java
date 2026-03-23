package com.supermap.udbx.geometry.cad;

import com.supermap.udbx.core.GeometryType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * CAD Geometry 解码器。
 *
 * <p>解码 SpatiaLite BLOB 中以 GeoHeader 格式存储的 SuperMap CAD 几何对象（白皮书 §4.3）。
 *
 * <p>GeoHeader 格式：
 * <pre>
 *   int32  geoType    // 对象类型，见表 25
 *   int32  styleSize  // 风格字节数（0 表示无风格）
 *   byte[] style      // styleSize 字节的风格数据
 * </pre>
 *
 * <p>风格类型由 geoType 决定：
 * <ul>
 *   <li>点类型 (1)           → StyleMarker</li>
 *   <li>线/弧/曲线类 (3,24,25,27,28,29) → StyleLine</li>
 *   <li>面/参数化 (5,12,15,20,21) → StyleFill</li>
 * </ul>
 */
public final class CadGeometryReader {

    private CadGeometryReader() {}

    /**
     * 从字节数组解码 CAD 几何对象。
     *
     * @param bytes GeoHeader 格式的字节数组（Little-Endian）
     * @return 解码后的 {@link CadGeometry} 实例
     * @throws IllegalArgumentException 若 geoType 不支持或数据不合法
     */
    public static CadGeometry read(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return read(buf);
    }

    /**
     * 从 ByteBuffer 当前位置解码一个 CAD 几何对象（供 CadDataset 复用）。
     */
    public static CadGeometry read(ByteBuffer buf) {
        int geoTypeVal = buf.getInt();
        int styleSize  = buf.getInt();

        CadStyle style = null;
        if (styleSize > 0) {
            style = readStyle(buf, geoTypeVal, styleSize);
        }

        return switch (geoTypeVal) {
            case 1  -> readGeoPoint(buf, style);
            case 3  -> readGeoLine(buf, style);
            case 5  -> readGeoRegion(buf, style);
            case 7  -> readGeoPoint3D(buf, style);
            case 9  -> readGeoLine3D(buf, style);
            case 11 -> readGeoRegion3D(buf, style);
            case 12 -> readGeoRect(buf, style);
            case 15 -> readGeoCircle(buf, style);
            case 20 -> readGeoEllipse(buf, style);
            case 21 -> readGeoPie(buf, style);
            case 24 -> readGeoArc(buf, style);
            case 25 -> readGeoEllipticArc(buf, style);
            case 27 -> readCurveObject(buf, GeometryType.GeoCardinal, style);
            case 28 -> readCurveObject(buf, GeometryType.GeoCurve, style);
            case 29 -> readCurveObject(buf, GeometryType.GeoBSpline, style);
            default -> throw new IllegalArgumentException(
                    "Unsupported CAD geoType: " + geoTypeVal);
        };
    }

    // ── Style 解码 ────────────────────────────────────────────────────────────

    private static CadStyle readStyle(ByteBuffer buf, int geoTypeVal, int styleSize) {
        int startPos = buf.position();
        CadStyle style = switch (geoTypeVal) {
            case 1, 7          -> readStyleMarker(buf);
            case 3, 9, 24, 25, 27, 28, 29 -> readStyleLine(buf);
            default            -> readStyleFill(buf);   // 5,11,12,15,20,21
        };
        // 跳过未消耗的 style 字节（容错）
        int consumed = buf.position() - startPos;
        if (consumed < styleSize) {
            buf.position(buf.position() + (styleSize - consumed));
        }
        return style;
    }

    /**
     * 解码 StyleMarker（白皮书 §4.3.1.1）。
     *
     * <pre>
     *   length(4) + markerStyle(4) + markerSize(4) + markerAngle(4) + markerColor(4)
     *   + markerWidth(4) + markerHeight(4) + reservedLength(1) + reservedData[rLen+4]
     *   + fillOpaqueRate(1) + fillGradientType(1) + fillAngle(2)
     *   + fillCenterOffsetX(2) + fillCenterOffsetY(2) + fillBackcolor(4)
     * </pre>
     */
    private static StyleMarker readStyleMarker(ByteBuffer buf) {
        buf.getInt();                    // length（字节流总长，已由 styleSize 控制）
        int  markerStyle      = buf.getInt();
        int  markerSize       = buf.getInt();
        int  markerAngle      = buf.getInt();
        int  markerColor      = buf.getInt();
        int  markerWidth      = buf.getInt();
        int  markerHeight     = buf.getInt();
        int  reservedLength   = buf.get() & 0xFF;
        buf.position(buf.position() + reservedLength + 4); // reservedData
        byte fillOpaqueRate   = buf.get();
        byte fillGradientType = buf.get();
        short fillAngle       = buf.getShort();
        short fillCenterOffX  = buf.getShort();
        short fillCenterOffY  = buf.getShort();
        int  fillBackcolor    = buf.getInt();

        return new StyleMarker(markerStyle, markerSize, markerAngle, markerColor,
                markerWidth, markerHeight, fillOpaqueRate, fillGradientType,
                fillAngle, fillCenterOffX, fillCenterOffY, fillBackcolor);
    }

    /**
     * 解码 StyleLine（白皮书 §4.3.1.2）。
     *
     * <pre>
     *   lineStyle(4) + lineWidth(4) + lineColor(4)
     *   + reservedLength(1) + reservedData[rLen+4]
     * </pre>
     */
    private static StyleLine readStyleLine(ByteBuffer buf) {
        int lineStyle      = buf.getInt();
        int lineWidth      = buf.getInt();
        int lineColor      = buf.getInt();
        int reservedLength = buf.get() & 0xFF;
        buf.position(buf.position() + reservedLength + 4); // reservedData
        return new StyleLine(lineStyle, lineWidth, lineColor);
    }

    /**
     * 解码 StyleFill（白皮书 §4.3.1.3）。
     *
     * <pre>
     *   lineStyle(4)+lineWidth(4)+lineColor(4)+fillStyle(4)+fillForecolor(4)+fillBackcolor(4)
     *   +fillOpaquerate(1)+fillGadientType(1)+fillAngle(2)+fillCenterOffsetX(2)+fillCenterOffsetY(2)
     *   +reserved1Length(1)+reserved1Data[r1Len+4]
     *   +reserved2Length(1)+reserved2Data[r2Len+4]
     * </pre>
     */
    private static StyleFill readStyleFill(ByteBuffer buf) {
        int   lineStyle        = buf.getInt();
        int   lineWidth        = buf.getInt();
        int   lineColor        = buf.getInt();
        int   fillStyle        = buf.getInt();
        int   fillForecolor    = buf.getInt();
        int   fillBackcolor    = buf.getInt();
        byte  fillOpaquerate   = buf.get();
        byte  fillGadientType  = buf.get();
        short fillAngle        = buf.getShort();
        short fillCenterOffX   = buf.getShort();
        short fillCenterOffY   = buf.getShort();
        int   r1Len            = buf.get() & 0xFF;
        buf.position(buf.position() + r1Len + 4);
        int   r2Len            = buf.get() & 0xFF;
        buf.position(buf.position() + r2Len + 4);

        return new StyleFill(lineStyle, lineWidth, lineColor,
                fillStyle, fillForecolor, fillBackcolor,
                fillOpaquerate, fillGadientType,
                fillAngle, fillCenterOffX, fillCenterOffY);
    }

    // ── 点 ────────────────────────────────────────────────────────────────────

    /** GeoPoint: Point(x,y) */
    private static CadGeometry.GeoPoint readGeoPoint(ByteBuffer buf, CadStyle style) {
        double x = buf.getDouble();
        double y = buf.getDouble();
        return new CadGeometry.GeoPoint(x, y, style);
    }

    /** GeoPoint3D: PointZ(x,y,z) */
    private static CadGeometry.GeoPoint3D readGeoPoint3D(ByteBuffer buf, CadStyle style) {
        double x = buf.getDouble();
        double y = buf.getDouble();
        double z = buf.getDouble();
        return new CadGeometry.GeoPoint3D(x, y, z, style);
    }

    // ── 线 / 面（共用子对象结构）─────────────────────────────────────────────

    /**
     * 读取 numSub + subPointCounts + points（二维）。
     * GeoLine 和 GeoRegion 共用此结构。
     */
    private static int[] readSubCounts(ByteBuffer buf, int numSub) {
        int[] subPointCounts = new int[numSub];
        for (int i = 0; i < numSub; i++) {
            subPointCounts[i] = buf.getInt();
        }
        return subPointCounts;
    }

    private static double[][] readPoints2D(ByteBuffer buf, int totalPoints) {
        double[] xs = new double[totalPoints];
        double[] ys = new double[totalPoints];
        for (int i = 0; i < totalPoints; i++) {
            xs[i] = buf.getDouble();
            ys[i] = buf.getDouble();
        }
        return new double[][]{xs, ys};
    }

    /** 返回 [0]=xs, [1]=ys, [2]=zs，包装为 double[][][] 便于统一返回。 */
    private static double[][][] readPoints3D(ByteBuffer buf, int totalPoints) {
        double[] xs = new double[totalPoints];
        double[] ys = new double[totalPoints];
        double[] zs = new double[totalPoints];
        for (int i = 0; i < totalPoints; i++) {
            xs[i] = buf.getDouble();
            ys[i] = buf.getDouble();
            zs[i] = buf.getDouble();
        }
        return new double[][][]{{xs, ys, zs}};
    }

    private static int totalPoints(int[] subPointCounts) {
        int total = 0;
        for (int c : subPointCounts) total += c;
        return total;
    }

    /** GeoLine: numSub(4) + subPointCount[numSub](4*n) + pnts[all](16*n) */
    private static CadGeometry.GeoLine readGeoLine(ByteBuffer buf, CadStyle style) {
        int numSub = buf.getInt();
        int[] subCounts = readSubCounts(buf, numSub);
        double[][] pts = readPoints2D(buf, totalPoints(subCounts));
        return new CadGeometry.GeoLine(numSub, subCounts, pts[0], pts[1], style);
    }

    /** GeoLine3D: numSub(4) + subPointCount[numSub](4*n) + pnts[all](24*n) */
    private static CadGeometry.GeoLine3D readGeoLine3D(ByteBuffer buf, CadStyle style) {
        int numSub = buf.getInt();
        int[] subCounts = readSubCounts(buf, numSub);
        double[][][] pts = readPoints3D(buf, totalPoints(subCounts));
        return new CadGeometry.GeoLine3D(numSub, subCounts, pts[0][0], pts[0][1], pts[0][2], style);
    }

    /** GeoRegion: 与 GeoLine 结构相同 */
    private static CadGeometry.GeoRegion readGeoRegion(ByteBuffer buf, CadStyle style) {
        int numSub = buf.getInt();
        int[] subCounts = readSubCounts(buf, numSub);
        double[][] pts = readPoints2D(buf, totalPoints(subCounts));
        return new CadGeometry.GeoRegion(numSub, subCounts, pts[0], pts[1], style);
    }

    /** GeoRegion3D: 与 GeoLine3D 结构相同 */
    private static CadGeometry.GeoRegion3D readGeoRegion3D(ByteBuffer buf, CadStyle style) {
        int numSub = buf.getInt();
        int[] subCounts = readSubCounts(buf, numSub);
        double[][][] pts = readPoints3D(buf, totalPoints(subCounts));
        return new CadGeometry.GeoRegion3D(numSub, subCounts, pts[0][0], pts[0][1], pts[0][2], style);
    }

    // ── 参数化几何 ────────────────────────────────────────────────────────────

    /** GeoRect: pntCenter(16) + width(8) + height(8) + angle(4) + reserved(4) */
    private static CadGeometry.GeoRect readGeoRect(ByteBuffer buf, CadStyle style) {
        double cx     = buf.getDouble();
        double cy     = buf.getDouble();
        double width  = buf.getDouble();
        double height = buf.getDouble();
        int    angle  = buf.getInt();
        buf.getInt(); // reserved
        return new CadGeometry.GeoRect(cx, cy, width, height, angle, style);
    }

    /** GeoCircle: pntCenter(16) + radius(8) */
    private static CadGeometry.GeoCircle readGeoCircle(ByteBuffer buf, CadStyle style) {
        double cx     = buf.getDouble();
        double cy     = buf.getDouble();
        double radius = buf.getDouble();
        return new CadGeometry.GeoCircle(cx, cy, radius, style);
    }

    /** GeoEllipse: pntCenter(16) + semimajor(8) + semiminor(8) + angle(4) + reserved(4) */
    private static CadGeometry.GeoEllipse readGeoEllipse(ByteBuffer buf, CadStyle style) {
        double cx    = buf.getDouble();
        double cy    = buf.getDouble();
        double major = buf.getDouble();
        double minor = buf.getDouble();
        int    angle = buf.getInt();
        buf.getInt(); // reserved
        return new CadGeometry.GeoEllipse(cx, cy, major, minor, angle, style);
    }

    /** GeoPie: pntCenter(16) + semimajor(8) + semiminor(8) + rotationangle(4) + startangle(4) + endangle(4) + reserved(4) */
    private static CadGeometry.GeoPie readGeoPie(ByteBuffer buf, CadStyle style) {
        double cx           = buf.getDouble();
        double cy           = buf.getDouble();
        double major        = buf.getDouble();
        double minor        = buf.getDouble();
        int    rotationAngle = buf.getInt();
        int    startAngle   = buf.getInt();
        int    endAngle     = buf.getInt();
        buf.getInt(); // reserved
        return new CadGeometry.GeoPie(cx, cy, major, minor, rotationAngle, startAngle, endAngle, style);
    }

    /** GeoArc: pntStart(16) + pntMiddle(16) + pntEnd(16) */
    private static CadGeometry.GeoArc readGeoArc(ByteBuffer buf, CadStyle style) {
        double sx = buf.getDouble(); double sy = buf.getDouble();
        double mx = buf.getDouble(); double my = buf.getDouble();
        double ex = buf.getDouble(); double ey = buf.getDouble();
        return new CadGeometry.GeoArc(sx, sy, mx, my, ex, ey, style);
    }

    /** GeoEllipticArc: pntCenter(16) + semimajor(8) + semiminor(8) + rotationangle(4) + startangle(4) + endangle(4) + reserved(4) */
    private static CadGeometry.GeoEllipticArc readGeoEllipticArc(ByteBuffer buf, CadStyle style) {
        double cx            = buf.getDouble();
        double cy            = buf.getDouble();
        double major         = buf.getDouble();
        double minor         = buf.getDouble();
        int    rotationAngle = buf.getInt();
        int    startAngle    = buf.getInt();
        int    endAngle      = buf.getInt();
        buf.getInt(); // reserved
        return new CadGeometry.GeoEllipticArc(cx, cy, major, minor, rotationAngle, startAngle, endAngle, style);
    }

    /** CurveObject: numPnts(4) + pnts[numPnts](16*n) */
    private static CadGeometry.GeoCurveObject readCurveObject(
            ByteBuffer buf, GeometryType type, CadStyle style) {
        int numPnts = buf.getInt();
        double[] xs = new double[numPnts];
        double[] ys = new double[numPnts];
        for (int i = 0; i < numPnts; i++) {
            xs[i] = buf.getDouble();
            ys[i] = buf.getDouble();
        }
        return new CadGeometry.GeoCurveObject(type, xs, ys, style);
    }
}
