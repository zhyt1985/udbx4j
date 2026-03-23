package com.supermap.udbx.geometry.cad;

import com.supermap.udbx.core.GeometryType;

/**
 * CAD 几何对象的密封接口。
 *
 * <p>每个子类型对应白皮书 §4.3 中的一种几何对象：
 * <ul>
 *   <li>{@link GeoPoint}       — 二维点 (geoType=1)</li>
 *   <li>{@link GeoLine}        — 二维线 (geoType=3)</li>
 *   <li>{@link GeoRegion}      — 二维面 (geoType=5)</li>
 *   <li>{@link GeoRect}        — 矩形 (geoType=12)</li>
 *   <li>{@link GeoCircle}      — 圆 (geoType=15)</li>
 *   <li>{@link GeoEllipse}     — 椭圆 (geoType=20)</li>
 *   <li>{@link GeoPie}         — 扇面 (geoType=21)</li>
 *   <li>{@link GeoArc}         — 圆弧 (geoType=24)</li>
 *   <li>{@link GeoEllipticArc} — 椭圆弧 (geoType=25)</li>
 *   <li>{@link GeoCurveObject} — 曲线（Cardinal=27/Curve=28/BSpline=29）</li>
 * </ul>
 */
public sealed interface CadGeometry
        permits CadGeometry.GeoPoint, CadGeometry.GeoLine, CadGeometry.GeoRegion,
                CadGeometry.GeoPoint3D, CadGeometry.GeoLine3D, CadGeometry.GeoRegion3D,
                CadGeometry.GeoRect, CadGeometry.GeoCircle, CadGeometry.GeoEllipse,
                CadGeometry.GeoPie, CadGeometry.GeoArc, CadGeometry.GeoEllipticArc,
                CadGeometry.GeoCurveObject {

    GeometryType geoType();
    CadStyle style();

    // ── 点 ──────────────────────────────────────────────────────────────────

    /**
     * 二维点对象（白皮书 §4.3.2）。
     */
    record GeoPoint(double x, double y, CadStyle style) implements CadGeometry {
        @Override public GeometryType geoType() { return GeometryType.GeoPoint; }
    }

    /**
     * 三维点对象（白皮书 §4.3.5）。
     */
    record GeoPoint3D(double x, double y, double z, CadStyle style) implements CadGeometry {
        @Override public GeometryType geoType() { return GeometryType.GeoPoint3D; }
    }

    // ── 线 / 面（子对象结构相同）─────────────────────────────────────────────

    /**
     * 二维线对象（白皮书 §4.3.3）。
     *
     * @param numSub         子对象（折线段）个数
     * @param subPointCounts 每个子对象的点数
     * @param xs             所有点的 X 坐标（扁平化）
     * @param ys             所有点的 Y 坐标（扁平化）
     */
    record GeoLine(int numSub, int[] subPointCounts, double[] xs, double[] ys, CadStyle style)
            implements CadGeometry {
        @Override public GeometryType geoType() { return GeometryType.GeoLine; }
    }

    /**
     * 三维线对象（白皮书 §4.3.6）。
     */
    record GeoLine3D(int numSub, int[] subPointCounts,
                     double[] xs, double[] ys, double[] zs, CadStyle style)
            implements CadGeometry {
        @Override public GeometryType geoType() { return GeometryType.GeoLine3D; }
    }

    /**
     * 二维面对象（白皮书 §4.3.4）。结构与 GeoLine 相同。
     */
    record GeoRegion(int numSub, int[] subPointCounts, double[] xs, double[] ys, CadStyle style)
            implements CadGeometry {
        @Override public GeometryType geoType() { return GeometryType.GeoRegion; }
    }

    /**
     * 三维面对象（白皮书 §4.3.7）。
     */
    record GeoRegion3D(int numSub, int[] subPointCounts,
                       double[] xs, double[] ys, double[] zs, CadStyle style)
            implements CadGeometry {
        @Override public GeometryType geoType() { return GeometryType.GeoRegion3D; }
    }

    // ── 参数化几何 ───────────────────────────────────────────────────────────

    /**
     * 矩形（白皮书 §4.3.8）。
     *
     * @param angle 旋转角度乘 10 的四舍五入整型值
     */
    record GeoRect(double centerX, double centerY, double width, double height,
                   int angle, CadStyle style) implements CadGeometry {
        @Override public GeometryType geoType() { return GeometryType.GeoRect; }
    }

    /**
     * 圆（白皮书 §4.3.10）。
     */
    record GeoCircle(double centerX, double centerY, double radius, CadStyle style)
            implements CadGeometry {
        @Override public GeometryType geoType() { return GeometryType.GeoCircle; }
    }

    /**
     * 椭圆（白皮书 §4.3.11）。
     *
     * @param angle 旋转角度乘 10 的四舍五入整型值
     */
    record GeoEllipse(double centerX, double centerY,
                      double semimajoraxis, double semiminoraxis,
                      int angle, CadStyle style) implements CadGeometry {
        @Override public GeometryType geoType() { return GeometryType.GeoEllipse; }
    }

    /**
     * 扇面（白皮书 §4.3.12）。
     *
     * @param rotationangle 旋转角度乘 10 的四舍五入整型值
     * @param startangle    起始角度乘 10 的四舍五入整型值
     * @param endangle      终止角度乘 10 的四舍五入整型值
     */
    record GeoPie(double centerX, double centerY,
                  double semimajoraxis, double semiminoraxis,
                  int rotationangle, int startangle, int endangle,
                  CadStyle style) implements CadGeometry {
        @Override public GeometryType geoType() { return GeometryType.GeoPie; }
    }

    /**
     * 圆弧（白皮书 §4.3.13）。由起始点、中间点、终止点三点定弧。
     */
    record GeoArc(double startX, double startY,
                  double middleX, double middleY,
                  double endX, double endY,
                  CadStyle style) implements CadGeometry {
        @Override public GeometryType geoType() { return GeometryType.GeoArc; }
    }

    /**
     * 椭圆弧（白皮书 §4.3.14）。
     */
    record GeoEllipticArc(double centerX, double centerY,
                          double semimajoraxis, double semiminoraxis,
                          int rotationangle, int startangle, int endangle,
                          CadStyle style) implements CadGeometry {
        @Override public GeometryType geoType() { return GeometryType.GeoEllipticArc; }
    }

    /**
     * 曲线对象（白皮书 §4.3.15），包含 Cardinal(27)、Curve(28)、BSpline(29)。
     *
     * @param type 具体曲线类型（GeometryType.GeoCardinal / GeoCurve / GeoBSpline）
     * @param xs   控制点 X 坐标数组
     * @param ys   控制点 Y 坐标数组
     */
    record GeoCurveObject(GeometryType type, double[] xs, double[] ys, CadStyle style)
            implements CadGeometry {
        @Override public GeometryType geoType() { return type; }
    }
}
