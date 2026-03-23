package com.supermap.udbx.geometry.cad;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * CAD Geometry 编码器。
 *
 * <p>将 {@link CadGeometry} 对象序列化为 GeoHeader 格式字节数组（Little-Endian）。
 *
 * <p>GeoHeader 格式：
 * <pre>
 *   int32  geoType    // 对象类型，见表 25
 *   int32  styleSize  // 风格字节数（0 表示无风格）
 *   byte[] style      // styleSize 字节的风格数据
 *   ...几何数据...
 * </pre>
 *
 * <p>风格序列化：reservedLength 始终写为 0，reservedData 写 4 字节零值。
 * StyleMarker 额外包含 int32 length 前缀。
 */
public final class CadGeometryWriter {

    private CadGeometryWriter() {}

    /**
     * 将 CAD 几何对象序列化为字节数组。
     *
     * @param geom 要编码的几何对象
     * @return GeoHeader 格式字节数组（Little-Endian）
     */
    public static byte[] write(CadGeometry geom) {
        byte[] styleBytes = encodeStyle(geom.style());
        byte[] geoBytes   = encodeGeometry(geom);

        int totalSize = 4 + 4 + styleBytes.length + geoBytes.length;
        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(cadTypeValue(geom));
        buf.putInt(styleBytes.length);
        buf.put(styleBytes);
        buf.put(geoBytes);
        return buf.array();
    }

    // ── geoType 整数值映射 ────────────────────────────────────────────────────

    private static int cadTypeValue(CadGeometry geom) {
        // GeoCurveObject 使用 geoType() 枚举直接获取数值
        return geom.geoType().getValue();
    }

    // ── Style 编码 ────────────────────────────────────────────────────────────

    private static byte[] encodeStyle(CadStyle style) {
        if (style == null) {
            return new byte[0];
        }
        if (style instanceof StyleMarker m) {
            return encodeStyleMarker(m);
        }
        if (style instanceof StyleLine l) {
            return encodeStyleLine(l);
        }
        if (style instanceof StyleFill f) {
            return encodeStyleFill(f);
        }
        throw new IllegalArgumentException("Unknown CadStyle type: " + style.getClass());
    }

    /**
     * StyleMarker 编码（白皮书 §4.3.1.1）：
     * <pre>
     *   length(4) + markerStyle(4) + markerSize(4) + markerAngle(4) + markerColor(4)
     *   + markerWidth(4) + markerHeight(4) + reservedLength(1) + reservedData[0+4]
     *   + fillOpaqueRate(1) + fillGradientType(1) + fillAngle(2)
     *   + fillCenterOffsetX(2) + fillCenterOffsetY(2) + fillBackcolor(4)
     * </pre>
     * reservedLength=0 → reservedData=4 字节零值。
     * 总计：4+4+4+4+4+4+4+1+4+1+1+2+2+2+4 = 45 字节（含 length 字段）。
     */
    private static byte[] encodeStyleMarker(StyleMarker m) {
        // body after length field = 41 bytes
        int bodySize = 4 + 4 + 4 + 4 + 4 + 4 + 1 + 4 + 1 + 1 + 2 + 2 + 2 + 4; // 41
        ByteBuffer buf = ByteBuffer.allocate(4 + bodySize).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(bodySize);               // length (= body byte count)
        buf.putInt(m.markerStyle());
        buf.putInt(m.markerSize());
        buf.putInt(m.markerAngle());
        buf.putInt(m.markerColor());
        buf.putInt(m.markerWidth());
        buf.putInt(m.markerHeight());
        buf.put((byte) 0);                  // reservedLength = 0
        buf.putInt(0);                      // reservedData[4]
        buf.put(m.fillOpaqueRate());
        buf.put(m.fillGradientType());
        buf.putShort(m.fillAngle());
        buf.putShort(m.fillCenterOffsetX());
        buf.putShort(m.fillCenterOffsetY());
        buf.putInt(m.fillBackcolor());
        return buf.array();
    }

    /**
     * StyleLine 编码（白皮书 §4.3.1.2）：
     * <pre>
     *   lineStyle(4) + lineWidth(4) + lineColor(4)
     *   + reservedLength(1) + reservedData[0+4]
     * </pre>
     * 总计：4+4+4+1+4 = 17 字节。
     */
    private static byte[] encodeStyleLine(StyleLine l) {
        ByteBuffer buf = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(l.lineStyle());
        buf.putInt(l.lineWidth());
        buf.putInt(l.lineColor());
        buf.put((byte) 0);  // reservedLength = 0
        buf.putInt(0);      // reservedData[4]
        return buf.array();
    }

    /**
     * StyleFill 编码（白皮书 §4.3.1.3）：
     * <pre>
     *   lineStyle(4)+lineWidth(4)+lineColor(4)+fillStyle(4)+fillForecolor(4)+fillBackcolor(4)
     *   +fillOpaquerate(1)+fillGadientType(1)+fillAngle(2)+fillCenterOffsetX(2)+fillCenterOffsetY(2)
     *   +reserved1Length(1)+reserved1Data[0+4]
     *   +reserved2Length(1)+reserved2Data[0+4]
     * </pre>
     * 总计：4+4+4+4+4+4+1+1+2+2+2+1+4+1+4 = 42 字节。
     */
    private static byte[] encodeStyleFill(StyleFill f) {
        ByteBuffer buf = ByteBuffer.allocate(42).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(f.lineStyle());
        buf.putInt(f.lineWidth());
        buf.putInt(f.lineColor());
        buf.putInt(f.fillStyle());
        buf.putInt(f.fillForecolor());
        buf.putInt(f.fillBackcolor());
        buf.put(f.fillOpaquerate());
        buf.put(f.fillGadientType());
        buf.putShort(f.fillAngle());
        buf.putShort(f.fillCenterOffsetX());
        buf.putShort(f.fillCenterOffsetY());
        buf.put((byte) 0); buf.putInt(0);  // reserved1Length=0 + reserved1Data[4]
        buf.put((byte) 0); buf.putInt(0);  // reserved2Length=0 + reserved2Data[4]
        return buf.array();
    }

    // ── 几何数据编码 ──────────────────────────────────────────────────────────

    private static byte[] encodeGeometry(CadGeometry geom) {
        if (geom instanceof CadGeometry.GeoPoint g)       return encodeGeoPoint(g);
        if (geom instanceof CadGeometry.GeoPoint3D g)     return encodeGeoPoint3D(g);
        if (geom instanceof CadGeometry.GeoLine g)        return encodeLineOrRegion2D(g.numSub(), g.subPointCounts(), g.xs(), g.ys());
        if (geom instanceof CadGeometry.GeoLine3D g)      return encodeLineOrRegion3D(g.numSub(), g.subPointCounts(), g.xs(), g.ys(), g.zs());
        if (geom instanceof CadGeometry.GeoRegion g)      return encodeLineOrRegion2D(g.numSub(), g.subPointCounts(), g.xs(), g.ys());
        if (geom instanceof CadGeometry.GeoRegion3D g)    return encodeLineOrRegion3D(g.numSub(), g.subPointCounts(), g.xs(), g.ys(), g.zs());
        if (geom instanceof CadGeometry.GeoRect g)        return encodeGeoRect(g);
        if (geom instanceof CadGeometry.GeoCircle g)      return encodeGeoCircle(g);
        if (geom instanceof CadGeometry.GeoEllipse g)     return encodeGeoEllipse(g);
        if (geom instanceof CadGeometry.GeoPie g)         return encodeGeoPie(g);
        if (geom instanceof CadGeometry.GeoArc g)         return encodeGeoArc(g);
        if (geom instanceof CadGeometry.GeoEllipticArc g) return encodeGeoEllipticArc(g);
        if (geom instanceof CadGeometry.GeoCurveObject g) return encodeCurveObject(g);
        throw new IllegalArgumentException("Unknown CadGeometry type: " + geom.getClass());
    }

    // ── 点 ───────────────────────────────────────────────────────────────────

    private static byte[] encodeGeoPoint(CadGeometry.GeoPoint g) {
        ByteBuffer buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buf.putDouble(g.x());
        buf.putDouble(g.y());
        return buf.array();
    }

    private static byte[] encodeGeoPoint3D(CadGeometry.GeoPoint3D g) {
        ByteBuffer buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        buf.putDouble(g.x());
        buf.putDouble(g.y());
        buf.putDouble(g.z());
        return buf.array();
    }

    // ── 线 / 面 ──────────────────────────────────────────────────────────────

    private static byte[] encodeLineOrRegion2D(int numSub, int[] subCounts, double[] xs, double[] ys) {
        int totalPts = xs.length;
        ByteBuffer buf = ByteBuffer.allocate(4 + numSub * 4 + totalPts * 16)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(numSub);
        for (int c : subCounts) buf.putInt(c);
        for (int i = 0; i < totalPts; i++) {
            buf.putDouble(xs[i]);
            buf.putDouble(ys[i]);
        }
        return buf.array();
    }

    private static byte[] encodeLineOrRegion3D(int numSub, int[] subCounts,
                                                double[] xs, double[] ys, double[] zs) {
        int totalPts = xs.length;
        ByteBuffer buf = ByteBuffer.allocate(4 + numSub * 4 + totalPts * 24)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(numSub);
        for (int c : subCounts) buf.putInt(c);
        for (int i = 0; i < totalPts; i++) {
            buf.putDouble(xs[i]);
            buf.putDouble(ys[i]);
            buf.putDouble(zs[i]);
        }
        return buf.array();
    }

    // ── 参数化几何 ────────────────────────────────────────────────────────────

    private static byte[] encodeGeoRect(CadGeometry.GeoRect g) {
        ByteBuffer buf = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
        buf.putDouble(g.centerX());
        buf.putDouble(g.centerY());
        buf.putDouble(g.width());
        buf.putDouble(g.height());
        buf.putInt(g.angle());
        buf.putInt(0); // reserved
        return buf.array();
    }

    private static byte[] encodeGeoCircle(CadGeometry.GeoCircle g) {
        ByteBuffer buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        buf.putDouble(g.centerX());
        buf.putDouble(g.centerY());
        buf.putDouble(g.radius());
        return buf.array();
    }

    private static byte[] encodeGeoEllipse(CadGeometry.GeoEllipse g) {
        ByteBuffer buf = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
        buf.putDouble(g.centerX());
        buf.putDouble(g.centerY());
        buf.putDouble(g.semimajoraxis());
        buf.putDouble(g.semiminoraxis());
        buf.putInt(g.angle());
        buf.putInt(0); // reserved
        return buf.array();
    }

    private static byte[] encodeGeoPie(CadGeometry.GeoPie g) {
        ByteBuffer buf = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        buf.putDouble(g.centerX());
        buf.putDouble(g.centerY());
        buf.putDouble(g.semimajoraxis());
        buf.putDouble(g.semiminoraxis());
        buf.putInt(g.rotationangle());
        buf.putInt(g.startangle());
        buf.putInt(g.endangle());
        buf.putInt(0); // reserved
        return buf.array();
    }

    private static byte[] encodeGeoArc(CadGeometry.GeoArc g) {
        ByteBuffer buf = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        buf.putDouble(g.startX());
        buf.putDouble(g.startY());
        buf.putDouble(g.middleX());
        buf.putDouble(g.middleY());
        buf.putDouble(g.endX());
        buf.putDouble(g.endY());
        return buf.array();
    }

    private static byte[] encodeGeoEllipticArc(CadGeometry.GeoEllipticArc g) {
        ByteBuffer buf = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        buf.putDouble(g.centerX());
        buf.putDouble(g.centerY());
        buf.putDouble(g.semimajoraxis());
        buf.putDouble(g.semiminoraxis());
        buf.putInt(g.rotationangle());
        buf.putInt(g.startangle());
        buf.putInt(g.endangle());
        buf.putInt(0); // reserved
        return buf.array();
    }

    private static byte[] encodeCurveObject(CadGeometry.GeoCurveObject g) {
        int numPnts = g.xs().length;
        ByteBuffer buf = ByteBuffer.allocate(4 + numPnts * 16).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(numPnts);
        for (int i = 0; i < numPnts; i++) {
            buf.putDouble(g.xs()[i]);
            buf.putDouble(g.ys()[i]);
        }
        return buf.array();
    }
}
