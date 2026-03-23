package com.supermap.udbx.integration;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.CadDataset;
import com.supermap.udbx.dataset.CadFeature;
import com.supermap.udbx.geometry.cad.CadGeometry;
import com.supermap.udbx.geometry.cad.StyleLine;
import com.supermap.udbx.geometry.cad.StyleMarker;
import com.supermap.udbx.geometry.cad.StyleFill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：CAD 数据集写入（createCadDataset + addFeature + 往返读取验证）。
 */
class CadDatasetWriteTest {

    @TempDir
    Path tempDir;

    /** 默认点风格（全零值） */
    private static final StyleMarker MARKER = new StyleMarker(0, 0, 0, 0, 0, 0, (byte) 0, (byte) 0, (short) 0, (short) 0, (short) 0, 0);
    /** 默认线风格 */
    private static final StyleLine LINE_STYLE = new StyleLine(0, 1, 0xFF000000);
    /** 默认面风格 */
    private static final StyleFill FILL_STYLE = new StyleFill(0, 0, 0xFF000000, 0, 0xFFFFFFFF, 0xFFFFFFFF, (byte) 100, (byte) 0, (short) 0, (short) 0, (short) 0);

    private Path newUdbx(String name) {
        return tempDir.resolve(name + ".udbx");
    }

    @Test
    void create_cad_dataset_should_be_readable_back() {
        Path path = newUdbx("cad_point");

        try (UdbxDataSource ds = UdbxDataSource.create(path.toString())) {
            CadDataset cad = ds.createCadDataset("MyCAD");
            cad.addFeature(1, new CadGeometry.GeoPoint(1.0, 2.0, MARKER), Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path.toString())) {
            CadDataset cad = (CadDataset) ds.getDataset("MyCAD");
            assertThat(cad).isNotNull();
            List<CadFeature> features = cad.getFeatures();
            assertThat(features).hasSize(1);
            CadFeature f = features.get(0);
            assertThat(f.smId()).isEqualTo(1);
            assertThat(f.geometry()).isInstanceOf(CadGeometry.GeoPoint.class);
        }
    }

    @Test
    void cad_geopoint_roundtrip_preserves_coordinates() {
        Path path = newUdbx("cad_roundtrip");

        try (UdbxDataSource ds = UdbxDataSource.create(path.toString())) {
            CadDataset cad = ds.createCadDataset("RoundTripCAD");
            cad.addFeature(1, new CadGeometry.GeoPoint(116.39, 39.91, MARKER), Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path.toString())) {
            CadDataset cad = (CadDataset) ds.getDataset("RoundTripCAD");
            CadFeature feature = cad.getFeature(1);
            assertThat(feature).isNotNull();

            CadGeometry.GeoPoint point = (CadGeometry.GeoPoint) feature.geometry();
            assertThat(point.x()).isEqualTo(116.39);
            assertThat(point.y()).isEqualTo(39.91);
        }
    }

    @Test
    void cad_geoline_roundtrip_preserves_point_count() {
        Path path = newUdbx("cad_line");
        double[] xs = {0.0, 1.0, 2.0};
        double[] ys = {0.0, 1.0, 0.0};

        try (UdbxDataSource ds = UdbxDataSource.create(path.toString())) {
            CadDataset cad = ds.createCadDataset("LineCAD");
            cad.addFeature(1, new CadGeometry.GeoLine(1, new int[]{3}, xs, ys, LINE_STYLE), Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path.toString())) {
            CadDataset cad = (CadDataset) ds.getDataset("LineCAD");
            CadFeature feature = cad.getFeature(1);
            CadGeometry.GeoLine line = (CadGeometry.GeoLine) feature.geometry();
            assertThat(line.subPointCounts()).hasSize(1);
            assertThat(line.subPointCounts()[0]).isEqualTo(3);
            assertThat(line.xs()).containsExactly(0.0, 1.0, 2.0);
            assertThat(line.ys()).containsExactly(0.0, 1.0, 0.0);
        }
    }

    @Test
    void cad_georegion_roundtrip() {
        Path path = newUdbx("cad_region");
        double[] xs = {0.0, 1.0, 1.0, 0.0, 0.0};
        double[] ys = {0.0, 0.0, 1.0, 1.0, 0.0};

        try (UdbxDataSource ds = UdbxDataSource.create(path.toString())) {
            CadDataset cad = ds.createCadDataset("RegionCAD");
            cad.addFeature(1, new CadGeometry.GeoRegion(1, new int[]{5}, xs, ys, FILL_STYLE), Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path.toString())) {
            CadDataset cad = (CadDataset) ds.getDataset("RegionCAD");
            CadFeature feature = cad.getFeature(1);
            assertThat(feature.geometry()).isInstanceOf(CadGeometry.GeoRegion.class);
            CadGeometry.GeoRegion region = (CadGeometry.GeoRegion) feature.geometry();
            assertThat(region.subPointCounts()[0]).isEqualTo(5);
        }
    }

    @Test
    void multiple_cad_features_smid_and_count() {
        Path path = newUdbx("cad_multi");

        try (UdbxDataSource ds = UdbxDataSource.create(path.toString())) {
            CadDataset cad = ds.createCadDataset("MultiCAD");
            cad.addFeature(1, new CadGeometry.GeoPoint(1.0, 2.0, MARKER), Map.of());
            cad.addFeature(2, new CadGeometry.GeoPoint(3.0, 4.0, MARKER), Map.of());
            cad.addFeature(3, new CadGeometry.GeoPoint(5.0, 6.0, MARKER), Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path.toString())) {
            CadDataset cad = (CadDataset) ds.getDataset("MultiCAD");
            List<CadFeature> features = cad.getFeatures();
            assertThat(features).hasSize(3);
            assertThat(features.get(0).smId()).isEqualTo(1);
            assertThat(features.get(2).smId()).isEqualTo(3);
        }
    }

    @Test
    void getfeature_nonexistent_returns_null_after_write() {
        Path path = newUdbx("cad_null");

        try (UdbxDataSource ds = UdbxDataSource.create(path.toString())) {
            CadDataset cad = ds.createCadDataset("NullCAD");
            cad.addFeature(1, new CadGeometry.GeoPoint(0.0, 0.0, MARKER), Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path.toString())) {
            CadDataset cad = (CadDataset) ds.getDataset("NullCAD");
            assertThat(cad.getFeature(99999)).isNull();
        }
    }
}
