package com.supermap.udbx.spec;

import com.supermap.udbx.geometry.gaia.GaiaGeometryReader;
import com.supermap.udbx.geometry.gaia.GaiaGeometryWriter;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Spec 测试：GAIA Geometry 往返测试（encode → decode）。
 *
 * <p>验证 GaiaGeometryWriter 编码后的字节序列能被 GaiaGeometryReader 正确解码。
 * 这是编码实现正确性的最终验证。
 *
 * <p>对应白皮书 §4.2（GAIA 格式定义）。
 */
class GaiaGeometryRoundTripSpecTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    // ── Point 往返测试 ─────────────────────────────────────────────────────

    @Test
    void point_2d_round_trip_must_preserve_coordinates() {
        Point original = FACTORY.createPoint(new Coordinate(116.3912, 39.9075));

        byte[] encoded = GaiaGeometryWriter.writePoint(original, 4326);
        Point decoded = GaiaGeometryReader.readPoint(encoded);

        assertThat(decoded.getX()).isCloseTo(116.3912, offset(1e-10));
        assertThat(decoded.getY()).isCloseTo(39.9075, offset(1e-10));
        assertThat(decoded.getSRID()).isEqualTo(4326);
    }

    @Test
    void point_z_round_trip_must_preserve_coordinates() {
        Point original = FACTORY.createPoint(new Coordinate(116.3912, 39.9075, 100.0));

        byte[] encoded = GaiaGeometryWriter.writePointZ(original, 4326);
        Point decoded = GaiaGeometryReader.readPointZ(encoded);

        assertThat(decoded.getX()).isCloseTo(116.3912, offset(1e-10));
        assertThat(decoded.getY()).isCloseTo(39.9075, offset(1e-10));
        assertThat(decoded.getCoordinate().getZ()).isCloseTo(100.0, offset(1e-10));
        assertThat(decoded.getSRID()).isEqualTo(4326);
    }

    // ── MultiLineString 往返测试 ────────────────────────────────────────────

    @Test
    void multilinestring_2d_round_trip_must_preserve_geometry() {
        LineString[] lines = new LineString[2];
        lines[0] = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(0, 0),
                new Coordinate(1, 1),
                new Coordinate(2, 0)
        });
        lines[1] = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(3, 3),
                new Coordinate(4, 4)
        });
        MultiLineString original = FACTORY.createMultiLineString(lines);

        byte[] encoded = GaiaGeometryWriter.writeMultiLineString(original, 4326);
        MultiLineString decoded = GaiaGeometryReader.readMultiLineString(encoded);

        assertThat(decoded.getNumGeometries()).isEqualTo(2);
        assertThat(decoded.getGeometryN(0).getCoordinates()).hasSize(3);
        assertThat(decoded.getGeometryN(1).getCoordinates()).hasSize(2);
        assertThat(decoded.getSRID()).isEqualTo(4326);
    }

    @Test
    void multilinestring_z_round_trip_must_preserve_coordinates() {
        LineString[] lines = new LineString[1];
        lines[0] = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(0, 0, 10.0),
                new Coordinate(1, 1, 20.0)
        });
        MultiLineString original = FACTORY.createMultiLineString(lines);

        byte[] encoded = GaiaGeometryWriter.writeMultiLineStringZ(original, 4326);
        MultiLineString decoded = GaiaGeometryReader.readMultiLineStringZ(encoded);

        assertThat(decoded.getNumGeometries()).isEqualTo(1);
        Coordinate[] coords = decoded.getGeometryN(0).getCoordinates();
        assertThat(coords[0].getZ()).isCloseTo(10.0, offset(1e-10));
        assertThat(coords[1].getZ()).isCloseTo(20.0, offset(1e-10));
    }

    // ── MultiPolygon 往返测试 ───────────────────────────────────────────────

    @Test
    void multipolygon_2d_round_trip_must_preserve_geometry() {
        LinearRing shell = FACTORY.createLinearRing(new Coordinate[]{
                new Coordinate(0, 0),
                new Coordinate(10, 0),
                new Coordinate(10, 10),
                new Coordinate(0, 10),
                new Coordinate(0, 0)
        });
        Polygon polygon = FACTORY.createPolygon(shell);
        MultiPolygon original = FACTORY.createMultiPolygon(new Polygon[]{polygon});

        byte[] encoded = GaiaGeometryWriter.writeMultiPolygon(original, 4326);
        MultiPolygon decoded = GaiaGeometryReader.readMultiPolygon(encoded);

        assertThat(decoded.getNumGeometries()).isEqualTo(1);
        assertThat(decoded.getSRID()).isEqualTo(4326);
    }

    @Test
    void multipolygon_2d_with_holes_round_trip_must_preserve_rings() {
        LinearRing shell = FACTORY.createLinearRing(new Coordinate[]{
                new Coordinate(0, 0),
                new Coordinate(10, 0),
                new Coordinate(10, 10),
                new Coordinate(0, 10),
                new Coordinate(0, 0)
        });
        LinearRing hole = FACTORY.createLinearRing(new Coordinate[]{
                new Coordinate(2, 2),
                new Coordinate(8, 2),
                new Coordinate(8, 8),
                new Coordinate(2, 8),
                new Coordinate(2, 2)
        });
        Polygon polygon = FACTORY.createPolygon(shell, new LinearRing[]{hole});
        MultiPolygon original = FACTORY.createMultiPolygon(new Polygon[]{polygon});

        byte[] encoded = GaiaGeometryWriter.writeMultiPolygon(original, 4326);
        MultiPolygon decoded = GaiaGeometryReader.readMultiPolygon(encoded);

        assertThat(decoded.getNumGeometries()).isEqualTo(1);
        Polygon decodedPoly = (Polygon) decoded.getGeometryN(0);
        assertThat(decodedPoly.getNumInteriorRing()).isEqualTo(1);
    }

    @Test
    void multipolygon_z_round_trip_must_preserve_coordinates() {
        LinearRing shell = FACTORY.createLinearRing(new Coordinate[]{
                new Coordinate(0, 0, 100.0),
                new Coordinate(10, 0, 100.0),
                new Coordinate(10, 10, 100.0),
                new Coordinate(0, 10, 100.0),
                new Coordinate(0, 0, 100.0)
        });
        Polygon polygon = FACTORY.createPolygon(shell);
        MultiPolygon original = FACTORY.createMultiPolygon(new Polygon[]{polygon});

        byte[] encoded = GaiaGeometryWriter.writeMultiPolygonZ(original, 4326);
        MultiPolygon decoded = GaiaGeometryReader.readMultiPolygonZ(encoded);

        assertThat(decoded.getNumGeometries()).isEqualTo(1);
        Coordinate[] coords = ((Polygon) decoded.getGeometryN(0)).getExteriorRing().getCoordinates();
        assertThat(coords[0].getZ()).isCloseTo(100.0, offset(1e-10));
    }

    // ── 编码字节结构验证 ────────────────────────────────────────────────────

    @Test
    void point_2d_encoded_bytes_must_start_with_gaia_magic() {
        Point point = FACTORY.createPoint(new Coordinate(1.0, 2.0));

        byte[] encoded = GaiaGeometryWriter.writePoint(point, 4326);

        assertThat(encoded[0]).isEqualTo((byte) 0x00); // gaiaStart
        assertThat(encoded[1]).isEqualTo((byte) 0x01); // byteOrder (Little-Endian)
    }

    @Test
    void point_2d_encoded_bytes_must_end_with_gaia_end() {
        Point point = FACTORY.createPoint(new Coordinate(1.0, 2.0));

        byte[] encoded = GaiaGeometryWriter.writePoint(point, 4326);

        assertThat(encoded[encoded.length - 1]).isEqualTo((byte) 0xFE); // gaiaEnd
    }

    @Test
    void point_2d_encoded_bytes_must_contain_correct_geotype() {
        Point point = FACTORY.createPoint(new Coordinate(1.0, 2.0));

        byte[] encoded = GaiaGeometryWriter.writePoint(point, 4326);

        // geoType at offset 39 (Little-Endian int32)
        // 1 = Point
        assertThat(encoded[39]).isEqualTo((byte) 0x01);
        assertThat(encoded[40]).isEqualTo((byte) 0x00);
        assertThat(encoded[41]).isEqualTo((byte) 0x00);
        assertThat(encoded[42]).isEqualTo((byte) 0x00);
    }

    @Test
    void point_z_encoded_bytes_must_contain_correct_geotype() {
        Point point = FACTORY.createPoint(new Coordinate(1.0, 2.0, 3.0));

        byte[] encoded = GaiaGeometryWriter.writePointZ(point, 4326);

        // geoType at offset 39 (Little-Endian int32)
        // 1001 = 0xE9 0x03 0x00 0x00 in Little-Endian
        assertThat(encoded[39]).isEqualTo((byte) 0xE9); // 1001 & 0xFF = 233 = 0xE9
        assertThat(encoded[40]).isEqualTo((byte) 0x03); // 1001 >> 8 = 3
        assertThat(encoded[41]).isEqualTo((byte) 0x00);
        assertThat(encoded[42]).isEqualTo((byte) 0x00);
    }
}
