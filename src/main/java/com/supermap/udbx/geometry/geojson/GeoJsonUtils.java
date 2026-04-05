package com.supermap.udbx.geometry.geojson;

import com.supermap.udbx.geometry.GeometryFactoryPool;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JTS ↔ GeoJSON-like 转换工具类。
 *
 * <p>提供 JTS 几何对象与 udbx4spec 定义的 GeoJSON-like 结构之间的双向转换。
 * 参见 udbx4spec/docs/02-geometry-model.md §5。
 *
 * <p>使用示例：
 * <pre>{@code
 * // JTS → GeoJSON-like
 * Point jtsPoint = new GeometryFactory().createPoint(new Coordinate(116.4, 39.9));
 * PointGeometry geoJson = GeoJsonUtils.fromJts(jtsPoint);
 *
 * // GeoJSON-like → JTS
 * Point restored = GeoJsonUtils.toJts(geoJson, 4326);
 * }</pre>
 */
public final class GeoJsonUtils {

    private GeoJsonUtils() {
        // 工具类，禁止实例化
    }

    // ── GeoJSON-like → JTS ────────────────────────────────────────────────

    /**
     * 将 GeoJSON-like 点转换为 JTS Point。
     *
     * @param g    GeoJSON-like 点几何
     * @param srid 默认 SRID（若 g 中未指定则使用此值）
     * @return JTS Point（SRID 已设置）
     */
    public static Point toJts(GeoJsonGeometry.PointGeometry g, int srid) {
        int sridToUse = g.getSrid().orElse(srid);
        GeometryFactory factory = GeometryFactoryPool.getFactory(sridToUse);

        List<Double> coords = g.coordinates();
        Coordinate coord;
        if (coords.size() >= 3) {
            coord = new Coordinate(coords.get(0), coords.get(1), coords.get(2));
        } else {
            coord = new CoordinateXY(coords.get(0), coords.get(1));
        }

        return factory.createPoint(coord);
    }

    /**
     * 将 GeoJSON-like 多线转换为 JTS MultiLineString。
     *
     * @param g    GeoJSON-like 多线几何
     * @param srid 默认 SRID
     * @return JTS MultiLineString
     */
    public static MultiLineString toJts(GeoJsonGeometry.MultiLineStringGeometry g, int srid) {
        int sridToUse = g.getSrid().orElse(srid);
        GeometryFactory factory = GeometryFactoryPool.getFactory(sridToUse);
        boolean hasZ = g.getHasZ().orElse(false);

        org.locationtech.jts.geom.LineString[] lineStrings = new org.locationtech.jts.geom.LineString[g.coordinates().size()];
        for (int i = 0; i < g.coordinates().size(); i++) {
            List<List<Double>> line = g.coordinates().get(i);
            Coordinate[] coords = new Coordinate[line.size()];
            for (int j = 0; j < line.size(); j++) {
                List<Double> pt = line.get(j);
                if (hasZ || pt.size() >= 3) {
                    coords[j] = new Coordinate(pt.get(0), pt.get(1), pt.get(2));
                } else {
                    coords[j] = new CoordinateXY(pt.get(0), pt.get(1));
                }
            }
            lineStrings[i] = factory.createLineString(coords);
        }

        return factory.createMultiLineString(lineStrings);
    }

    /**
     * 将 GeoJSON-like 多面转换为 JTS MultiPolygon。
     *
     * @param g    GeoJSON-like 多面几何
     * @param srid 默认 SRID
     * @return JTS MultiPolygon
     */
    public static MultiPolygon toJts(GeoJsonGeometry.MultiPolygonGeometry g, int srid) {
        int sridToUse = g.getSrid().orElse(srid);
        GeometryFactory factory = GeometryFactoryPool.getFactory(sridToUse);
        boolean hasZ = g.getHasZ().orElse(false);

        Polygon[] polygons = new Polygon[g.coordinates().size()];
        for (int i = 0; i < g.coordinates().size(); i++) {
            List<List<List<Double>>> polygonRings = g.coordinates().get(i);
            LinearRing shell = toLinearRing(factory, polygonRings.get(0), hasZ);

            LinearRing[] holes = new LinearRing[polygonRings.size() - 1];
            for (int h = 1; h < polygonRings.size(); h++) {
                holes[h - 1] = toLinearRing(factory, polygonRings.get(h), hasZ);
            }

            polygons[i] = factory.createPolygon(shell, holes);
        }

        return factory.createMultiPolygon(polygons);
    }

    private static LinearRing toLinearRing(GeometryFactory factory, List<List<Double>> ring, boolean hasZ) {
        Coordinate[] coords = new Coordinate[ring.size()];
        for (int i = 0; i < ring.size(); i++) {
            List<Double> pt = ring.get(i);
            if (hasZ || pt.size() >= 3) {
                coords[i] = new Coordinate(pt.get(0), pt.get(1), pt.get(2));
            } else {
                coords[i] = new CoordinateXY(pt.get(0), pt.get(1));
            }
        }
        return factory.createLinearRing(coords);
    }

    // ── JTS → GeoJSON-like ────────────────────────────────────────────────

    /**
     * 将 JTS Point 转换为 GeoJSON-like 点几何。
     *
     * @param p JTS Point
     * @return GeoJSON-like 点几何（SRID 从 Point 的 SRID 取）
     */
    public static GeoJsonGeometry.PointGeometry fromJts(Point p) {
        List<Double> coords;
        if (Double.isNaN(p.getCoordinate().getZ())) {
            coords = List.of(p.getX(), p.getY());
        } else {
            coords = List.of(p.getX(), p.getY(), p.getCoordinate().getZ());
        }

        Integer srid = p.getSRID() > 0 ? p.getSRID() : null;
        boolean hasZ = !Double.isNaN(p.getCoordinate().getZ());
        List<Double> bbox = computeBbox(p);

        return new GeoJsonGeometry.PointGeometry(coords, srid, hasZ, bbox);
    }

    /**
     * 将 JTS MultiLineString 转换为 GeoJSON-like 多线几何。
     */
    public static GeoJsonGeometry.MultiLineStringGeometry fromJts(MultiLineString ml) {
        List<List<List<Double>>> coords = new ArrayList<>();
        boolean hasZ = false;

        for (int i = 0; i < ml.getNumGeometries(); i++) {
            org.locationtech.jts.geom.LineString line = (org.locationtech.jts.geom.LineString) ml.getGeometryN(i);
            List<List<Double>> lineCoords = new ArrayList<>();
            for (Coordinate c : line.getCoordinates()) {
                if (Double.isNaN(c.getZ())) {
                    lineCoords.add(List.of(c.getX(), c.getY()));
                } else {
                    lineCoords.add(List.of(c.getX(), c.getY(), c.getZ()));
                    hasZ = true;
                }
            }
            coords.add(lineCoords);
        }

        Integer srid = ml.getSRID() > 0 ? ml.getSRID() : null;
        List<Double> bbox = computeBbox(ml);

        return new GeoJsonGeometry.MultiLineStringGeometry(coords, srid, hasZ, bbox);
    }

    /**
     * 将 JTS MultiPolygon 转换为 GeoJSON-like 多面几何。
     */
    public static GeoJsonGeometry.MultiPolygonGeometry fromJts(MultiPolygon mp) {
        List<List<List<List<Double>>>> coords = new ArrayList<>();
        boolean hasZ = false;

        for (int i = 0; i < mp.getNumGeometries(); i++) {
            Polygon polygon = (Polygon) mp.getGeometryN(i);
            List<List<List<Double>>> polygonRings = new ArrayList<>();

            // Shell
            polygonRings.add(toCoordList(polygon.getExteriorRing().getCoordinates()));

            // Holes
            for (int h = 0; h < polygon.getNumInteriorRing(); h++) {
                polygonRings.add(toCoordList(polygon.getInteriorRingN(h).getCoordinates()));
            }

            coords.add(polygonRings);

            // 检测 Z
            for (Coordinate c : polygon.getCoordinates()) {
                if (!Double.isNaN(c.getZ())) {
                    hasZ = true;
                    break;
                }
            }
        }

        Integer srid = mp.getSRID() > 0 ? mp.getSRID() : null;
        List<Double> bbox = computeBbox(mp);

        return new GeoJsonGeometry.MultiPolygonGeometry(coords, srid, hasZ, bbox);
    }

    // ── 辅助方法 ───────────────────────────────────────────────────────────

    private static List<List<Double>> toCoordList(Coordinate[] coordinates) {
        List<List<Double>> result = new ArrayList<>(coordinates.length);
        for (Coordinate c : coordinates) {
            if (Double.isNaN(c.getZ())) {
                result.add(List.of(c.getX(), c.getY()));
            } else {
                result.add(List.of(c.getX(), c.getY(), c.getZ()));
            }
        }
        return result;
    }

    private static List<Double> computeBbox(org.locationtech.jts.geom.Geometry geom) {
        if (geom.isEmpty()) return null;
        var env = geom.getEnvelopeInternal();
        return List.of(env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY());
    }
}
