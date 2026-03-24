package com.supermap.udbx.benchmark;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * 几何解码性能基准测试。
 *
 * <p>测试 GaiaGeometryReader 各方法的单次解码性能。
 * 用于验证 GeometryFactory 对象池优化的效果。
 *
 * <p>运行方式：
 * <pre>
 * mvn clean package -DskipTests
 * java -jar target/udbx4j-0.1.0-SNAPSHOT-benchmarks.jar
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Thread)
public class GeometryDecodeBenchmark {

    private static final GeometryFactory GEOM_FACTORY = new GeometryFactory();

    @State(Scope.Thread)
    public static class ByteBufferState {
        byte[] pointBytes;
        byte[] pointZBytes;
        byte[] lineStringBytes;
        byte[] multiLineStringBytes;
        byte[] polygonBytes;
        byte[] multiPolygonBytes;

        @Setup
        public void setup() throws Exception {
            // Point
            Point point = GEOM_FACTORY.createPoint(new Coordinate(116.404, 39.915));
            pointBytes = com.supermap.udbx.geometry.gaia.GaiaGeometryWriter.writePoint(point, 4326);

            // PointZ
            Point pointZ = GEOM_FACTORY.createPoint(new Coordinate(116.404, 39.915, 50.0));
            pointZBytes = com.supermap.udbx.geometry.gaia.GaiaGeometryWriter.writePointZ(pointZ, 4326);

            // LineString
            Coordinate[] lineCoords = new Coordinate[]{
                new Coordinate(116.404, 39.915),
                new Coordinate(116.414, 39.925),
                new Coordinate(116.424, 39.935)
            };
            LineString lineString = GEOM_FACTORY.createLineString(lineCoords);
            lineStringBytes = com.supermap.udbx.geometry.gaia.GaiaGeometryWriter.writeLineString(lineString, 4326);

            // MultiLineString
            LineString[] lineStrings = new LineString[]{
                GEOM_FACTORY.createLineString(new Coordinate[]{
                    new Coordinate(116.404, 39.915),
                    new Coordinate(116.414, 39.925)
                }),
                GEOM_FACTORY.createLineString(new Coordinate[]{
                    new Coordinate(116.424, 39.935),
                    new Coordinate(116.434, 39.945)
                })
            };
            org.locationtech.jts.geom.MultiLineString multiLineString =
                GEOM_FACTORY.createMultiLineString(lineStrings);
            multiLineStringBytes = com.supermap.udbx.geometry.gaia.GaiaGeometryWriter.writeMultiLineString(multiLineString, 4326);

            // Polygon
            Coordinate[] coords = new Coordinate[]{
                new Coordinate(116.404, 39.915),
                new Coordinate(116.424, 39.915),
                new Coordinate(116.424, 39.935),
                new Coordinate(116.404, 39.935),
                new Coordinate(116.404, 39.915)
            };
            Polygon polygon = GEOM_FACTORY.createPolygon(coords);
            polygonBytes = com.supermap.udbx.geometry.gaia.GaiaGeometryWriter.writePolygon(polygon, 4326);

            // MultiPolygon
            Polygon[] polygons = new Polygon[]{
                GEOM_FACTORY.createPolygon(new Coordinate[]{
                    new Coordinate(116.404, 39.915),
                    new Coordinate(116.414, 39.915),
                    new Coordinate(116.414, 39.925),
                    new Coordinate(116.404, 39.925),
                    new Coordinate(116.404, 39.915)
                }),
                GEOM_FACTORY.createPolygon(new Coordinate[]{
                    new Coordinate(116.424, 39.935),
                    new Coordinate(116.434, 39.935),
                    new Coordinate(116.434, 39.945),
                    new Coordinate(116.424, 39.945),
                    new Coordinate(116.424, 39.935)
                })
            };
            org.locationtech.jts.geom.MultiPolygon multiPolygon = GEOM_FACTORY.createMultiPolygon(polygons);
            multiPolygonBytes = com.supermap.udbx.geometry.gaia.GaiaGeometryWriter.writeMultiPolygon(multiPolygon, 4326);
        }
    }

    @Benchmark
    public Point decodePoint(ByteBufferState state) {
        return com.supermap.udbx.geometry.gaia.GaiaGeometryReader.readPoint(state.pointBytes);
    }

    @Benchmark
    public Point decodePointZ(ByteBufferState state) {
        return com.supermap.udbx.geometry.gaia.GaiaGeometryReader.readPointZ(state.pointZBytes);
    }

    @Benchmark
    public LineString decodeLineString(ByteBufferState state) {
        return com.supermap.udbx.geometry.gaia.GaiaGeometryReader.readLineString(state.lineStringBytes);
    }

    @Benchmark
    public org.locationtech.jts.geom.MultiLineString decodeMultiLineString(ByteBufferState state) {
        return com.supermap.udbx.geometry.gaia.GaiaGeometryReader.readMultiLineString(state.multiLineStringBytes);
    }

    @Benchmark
    public Polygon decodePolygon(ByteBufferState state) {
        return com.supermap.udbx.geometry.gaia.GaiaGeometryReader.readPolygon(state.polygonBytes);
    }

    @Benchmark
    public org.locationtech.jts.geom.MultiPolygon decodeMultiPolygon(ByteBufferState state) {
        return com.supermap.udbx.geometry.gaia.GaiaGeometryReader.readMultiPolygonAuto(state.multiPolygonBytes);
    }
}
