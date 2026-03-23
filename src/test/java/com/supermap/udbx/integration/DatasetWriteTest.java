package com.supermap.udbx.integration;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.core.FieldInfo;
import com.supermap.udbx.core.FieldType;
import com.supermap.udbx.dataset.LineDataset;
import com.supermap.udbx.dataset.LineFeature;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.dataset.PointFeature;
import com.supermap.udbx.dataset.RegionDataset;
import com.supermap.udbx.dataset.RegionFeature;
import com.supermap.udbx.dataset.TabularDataset;
import com.supermap.udbx.dataset.TabularRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * 集成测试：验证各数据集类型写入→读取往返正确性。
 *
 * <p>对应白皮书 §3.1 数据集写入（第一阶段只支持无用户字段的简单写入）。
 */
class DatasetWriteTest {

    private static final GeometryFactory GF = new GeometryFactory();

    // ── Point ─────────────────────────────────────────────────────────────────

    @Test
    void point_dataset_write_and_read_roundtrip(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("test.udbx").toString();

        // 写入 5 个 Point
        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointDataset pd = ds.createPointDataset("TestPoints", 4326);
            for (int i = 1; i <= 5; i++) {
                pd.addFeature(i, GF.createPoint(new Coordinate(i * 10.0, i * 5.0)), Map.of());
            }
        }

        // 重新打开并验证
        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            PointDataset pd = (PointDataset) ds.getDataset("TestPoints");
            assertThat(pd.getObjectCount()).isEqualTo(5);

            List<PointFeature> features = pd.getFeatures();
            assertThat(features).hasSize(5);
            assertThat(features.get(0).geometry().getX()).isCloseTo(10.0, offset(1e-9));
            assertThat(features.get(0).geometry().getY()).isCloseTo(5.0, offset(1e-9));
            assertThat(features.get(4).geometry().getX()).isCloseTo(50.0, offset(1e-9));
        }
    }

    @Test
    void point_dataset_smid_must_match(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("test.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointDataset pd = ds.createPointDataset("PtDs", 4326);
            pd.addFeature(42, GF.createPoint(new Coordinate(1.0, 2.0)), Map.of());
            pd.addFeature(100, GF.createPoint(new Coordinate(3.0, 4.0)), Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            PointDataset pd = (PointDataset) ds.getDataset("PtDs");
            assertThat(pd.getObjectCount()).isEqualTo(2);
            PointFeature f = pd.getFeature(42);
            assertThat(f).isNotNull();
            assertThat(f.geometry().getX()).isCloseTo(1.0, offset(1e-9));
        }
    }

    // ── Line ──────────────────────────────────────────────────────────────────

    @Test
    void line_dataset_write_and_read_roundtrip(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("test.udbx").toString();

        MultiLineString mls = GF.createMultiLineString(new org.locationtech.jts.geom.LineString[]{
                GF.createLineString(new Coordinate[]{
                        new Coordinate(0, 0), new Coordinate(1, 1), new Coordinate(2, 0)
                })
        });

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineDataset ld = ds.createLineDataset("TestLines", 4326);
            ld.addFeature(1, mls, Map.of());
            ld.addFeature(2, mls, Map.of());
            ld.addFeature(3, mls, Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            LineDataset ld = (LineDataset) ds.getDataset("TestLines");
            assertThat(ld.getObjectCount()).isEqualTo(3);
            List<LineFeature> features = ld.getFeatures();
            assertThat(features).hasSize(3);
        }
    }

    // ── Region ────────────────────────────────────────────────────────────────

    @Test
    void region_dataset_write_and_read_roundtrip(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("test.udbx").toString();

        LinearRing ring = GF.createLinearRing(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(1, 0),
                new Coordinate(1, 1), new Coordinate(0, 1),
                new Coordinate(0, 0)
        });
        Polygon poly = GF.createPolygon(ring);
        MultiPolygon mp = GF.createMultiPolygon(new Polygon[]{poly});

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionDataset rd = ds.createRegionDataset("TestRegions", 4326);
            rd.addFeature(1, mp, Map.of());
            rd.addFeature(2, mp, Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            RegionDataset rd = (RegionDataset) ds.getDataset("TestRegions");
            assertThat(rd.getObjectCount()).isEqualTo(2);
            List<RegionFeature> features = rd.getFeatures();
            assertThat(features).hasSize(2);
        }
    }

    // ── Tabular ───────────────────────────────────────────────────────────────

    @Test
    void tabular_dataset_write_and_read_roundtrip(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("test.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            TabularDataset td = ds.createTabularDataset("TestTable");
            td.addRow(1, Map.of());
            td.addRow(2, Map.of());
            td.addRow(3, Map.of());
            td.addRow(4, Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            TabularDataset td = (TabularDataset) ds.getDataset("TestTable");
            assertThat(td.getObjectCount()).isEqualTo(4);
            List<TabularRecord> records = td.getRecords();
            assertThat(records).hasSize(4);
        }
    }

    // ── 属性字段写入 ──────────────────────────────────────────────────────────

    @Test
    void point_dataset_with_user_fields_preserves_attributes(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("attrs.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "NAME", FieldType.NText, "名称", false),
                new FieldInfo(0, "VALUE", FieldType.Double, "数值", false)
        );

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointDataset pd = ds.createPointDataset("PtAttrs", 4326, fields);
            pd.addFeature(1, GF.createPoint(new Coordinate(10.0, 20.0)),
                    Map.of("NAME", "Beijing", "VALUE", 42.5));
            pd.addFeature(2, GF.createPoint(new Coordinate(11.0, 21.0)),
                    Map.of("NAME", "Shanghai", "VALUE", 99.0));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            PointDataset pd = (PointDataset) ds.getDataset("PtAttrs");
            PointFeature f1 = pd.getFeature(1);
            assertThat(f1.attributes()).containsEntry("NAME", "Beijing");
            assertThat(f1.attributes()).containsKey("VALUE");

            List<PointFeature> all = pd.getFeatures();
            assertThat(all).hasSize(2);
            assertThat(all.get(1).attributes()).containsEntry("NAME", "Shanghai");
        }
    }

    @Test
    void tabular_dataset_with_user_fields_preserves_attributes(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("tabular_attrs.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "CITY", FieldType.NText, "城市", false),
                new FieldInfo(0, "POP", FieldType.Int32, "人口", false)
        );

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            TabularDataset td = ds.createTabularDataset("CityTable", fields);
            td.addRow(1, Map.of("CITY", "Beijing", "POP", 21893095));
            td.addRow(2, Map.of("CITY", "Shanghai", "POP", 24870895));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            TabularDataset td = (TabularDataset) ds.getDataset("CityTable");
            List<TabularRecord> records = td.getRecords();
            assertThat(records).hasSize(2);
            assertThat(records.get(0).attributes()).containsEntry("CITY", "Beijing");
            assertThat(records.get(1).attributes()).containsEntry("CITY", "Shanghai");
        }
    }
}

