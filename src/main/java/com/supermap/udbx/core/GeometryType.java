package com.supermap.udbx.core;

/**
 * Geometry 类型枚举。
 *
 * <p>对应白皮书表 2 及 §4.2、§4.3 中的类型常量。
 *
 * <p>注意：GAIA 格式（§4.2）和 GeoHeader 格式（§4.3）存在重叠的整数值
 * （例如两者均用 1/3/5），因此提供 {@link #fromGaiaValue(int)} 和
 * {@link #fromCadValue(int)} 两个独立工厂方法。
 */
public enum GeometryType {

    // ── GAIA 格式（SpatiaLite §4.2）───────────────────────────────────────
    GAIAPoint(1),
    GAIAPolygon(3),
    GAIAMultiLineString(5),
    GAIAMultiPolygon(6),
    GAIAPointZ(1001),
    GAIAMultiLineStringZ(1005),
    GAIAMultiPolygonZ(1006),

    // ── GeoHeader 格式（SuperMap CAD §4.3）────────────────────────────────
    GeoPoint(1),
    GeoLine(3),
    GeoRegion(5),
    GeoPoint3D(7),
    GeoLine3D(9),
    GeoRegion3D(11),
    GeoRect(12),
    GeoCircle(15),
    GeoEllipse(20),
    GeoPie(21),
    GeoArc(24),
    GeoEllipticArc(25),
    GeoCardinal(27),
    GeoCurve(28),
    GeoBSpline(29);

    private final int value;

    GeometryType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    private static final GeometryType[] GAIA_TYPES = {
        GAIAPoint, GAIAPolygon, GAIAMultiLineString, GAIAMultiPolygon,
        GAIAPointZ, GAIAMultiLineStringZ, GAIAMultiPolygonZ
    };

    private static final GeometryType[] CAD_TYPES = {
        GeoPoint, GeoLine, GeoRegion, GeoPoint3D, GeoLine3D, GeoRegion3D,
        GeoRect, GeoCircle, GeoEllipse, GeoPie, GeoArc, GeoEllipticArc,
        GeoCardinal, GeoCurve, GeoBSpline
    };

    /** 从 GAIA 二进制的 geoType int32 值解析枚举。 */
    public static GeometryType fromGaiaValue(int value) {
        for (GeometryType type : GAIA_TYPES) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown GAIA geometry type value: " + value);
    }

    /** 从 GeoHeader 二进制的 geoType int32 值解析枚举。 */
    public static GeometryType fromCadValue(int value) {
        for (GeometryType type : CAD_TYPES) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown CAD geometry type value: " + value);
    }
}
