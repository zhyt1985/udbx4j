package com.supermap.udbx.integration;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.LineZDataset;
import com.supermap.udbx.dataset.PointZDataset;
import com.supermap.udbx.dataset.RegionZDataset;
import com.supermap.udbx.dataset.LineFeature;
import com.supermap.udbx.dataset.PointFeature;
import com.supermap.udbx.dataset.RegionFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * 集成测试：三维矢量数据集（PointZ/LineZ/RegionZ）写入→读取往返验证。
 */
class Vector3DDatasetWriteTest {

    private static final GeometryFactory GF = new GeometryFactory();

    @Test
    void pointz_dataset_roundtrip_preserves_xyz(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("pointz.udbx").toString();
        Coordinate coord3d = new Coordinate(116.39, 39.91, 50.5);

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointZDataset pd = ds.createPointZDataset("PointsZ", 4326);
            pd.addFeature(1, GF.createPoint(coord3d), Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            PointZDataset pd = (PointZDataset) ds.getDataset("PointsZ");
            assertThat(pd).isNotNull();
            List<PointFeature> features = pd.getFeatures();
            assertThat(features).hasSize(1);

            PointFeature f = features.get(0);
            assertThat(f.smId()).isEqualTo(1);
            assertThat(f.geometry().getX()).isCloseTo(116.39, offset(1e-9));
            assertThat(f.geometry().getY()).isCloseTo(39.91, offset(1e-9));
            assertThat(f.geometry().getCoordinate().getZ()).isCloseTo(50.5, offset(1e-9));
        }
    }

    @Test
    void linez_dataset_roundtrip(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("linez.udbx").toString();
        org.locationtech.jts.geom.MultiLineString mls3d = GF.createMultiLineString(
                new org.locationtech.jts.geom.LineString[]{
                        GF.createLineString(new Coordinate[]{
                                new Coordinate(0, 0, 10), new Coordinate(1, 1, 20), new Coordinate(2, 0, 30)
                        })
                });

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineZDataset ld = ds.createLineZDataset("LinesZ", 4326);
            ld.addFeature(1, mls3d, Map.of());
            ld.addFeature(2, mls3d, Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            LineZDataset ld = (LineZDataset) ds.getDataset("LinesZ");
            assertThat(ld).isNotNull();
            List<LineFeature> features = ld.getFeatures();
            assertThat(features).hasSize(2);
            assertThat(features.get(0).smId()).isEqualTo(1);
        }
    }

    @Test
    void regionz_dataset_roundtrip(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("regionz.udbx").toString();
        LinearRing ring3d = GF.createLinearRing(new Coordinate[]{
                new Coordinate(0, 0, 5), new Coordinate(1, 0, 5),
                new Coordinate(1, 1, 5), new Coordinate(0, 1, 5),
                new Coordinate(0, 0, 5)
        });
        Polygon poly = GF.createPolygon(ring3d);
        MultiPolygon mp = GF.createMultiPolygon(new Polygon[]{poly});

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionZDataset rd = ds.createRegionZDataset("RegionsZ", 4326);
            rd.addFeature(1, mp, Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            RegionZDataset rd = (RegionZDataset) ds.getDataset("RegionsZ");
            assertThat(rd).isNotNull();
            List<RegionFeature> features = rd.getFeatures();
            assertThat(features).hasSize(1);
            assertThat(features.get(0).smId()).isEqualTo(1);
        }
    }

    @Test
    void pointz_getfeature_nonexistent_returns_null(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("pointz_null.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointZDataset pd = ds.createPointZDataset("PtZ", 4326);
            pd.addFeature(1, GF.createPoint(new Coordinate(1, 2, 3)), Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            PointZDataset pd = (PointZDataset) ds.getDataset("PtZ");
            assertThat(pd.getFeature(99999)).isNull();
        }
    }
}
