package com.supermap.udbx.spec;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.core.FieldInfo;
import com.supermap.udbx.core.FieldType;
import com.supermap.udbx.dataset.*;
import com.supermap.udbx.geometry.cad.CadGeometry;
import com.supermap.udbx.geometry.cad.StyleFill;
import com.supermap.udbx.geometry.cad.StyleLine;
import com.supermap.udbx.geometry.cad.StyleMarker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 测试：RegionDataset、LineDataset、TabularDataset、CadDataset 的完整 CRUD 操作。
 */
class Dataset2DSpecTest {

    private static final GeometryFactory GF = new GeometryFactory();

    // ── 辅助方法 ─────────────────────────────────────────────────────────────

    /**
     * 构建一个简单的 2D MultiPolygon（正方形，srid=4326）。
     */
    private MultiPolygon buildMultiPolygon(double x0, double y0, double x1, double y1) {
        Coordinate[] coords = {
            new Coordinate(x0, y0),
            new Coordinate(x1, y0),
            new Coordinate(x1, y1),
            new Coordinate(x0, y1),
            new Coordinate(x0, y0)
        };
        LinearRing shell = GF.createLinearRing(coords);
        Polygon polygon = GF.createPolygon(shell, new LinearRing[0]);
        return GF.createMultiPolygon(new Polygon[]{polygon});
    }

    /**
     * 构建一个简单的 2D MultiLineString。
     */
    private MultiLineString buildMultiLineString(double x0, double y0, double x1, double y1) {
        Coordinate[] coords = {
            new Coordinate(x0, y0),
            new Coordinate(x1, y1)
        };
        LineString line = GF.createLineString(coords);
        return GF.createMultiLineString(new LineString[]{line});
    }

    /**
     * 构建默认的 StyleMarker（全零）。
     */
    private StyleMarker defaultStyleMarker() {
        return new StyleMarker(0, 0, 0, 0, 0, 0, (byte) 0, (byte) 0, (short) 0, (short) 0, (short) 0, 0);
    }

    /**
     * 构建默认的 StyleLine（全零）。
     */
    private StyleLine defaultStyleLine() {
        return new StyleLine(0, 0, 0);
    }

    /**
     * 构建默认的 StyleFill（全零）。
     */
    private StyleFill defaultStyleFill() {
        return new StyleFill(0, 0, 0, 0, 0, 0, (byte) 0, (byte) 0, (short) 0, (short) 0, (short) 0);
    }

    // ── RegionDataset 测试 ────────────────────────────────────────────────────

    @Test
    void region_addFeature_and_getFeatures_should_work(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("region.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionDataset rd = ds.createRegionDataset("Regions", 4326);
            MultiPolygon geom = buildMultiPolygon(0.0, 0.0, 1.0, 1.0);
            rd.insert(new RegionFeature(1, geom, Map.of()));

            List<RegionFeature> features = rd.list();
            assertThat(features).hasSize(1);
            assertThat(features.get(0).id()).isEqualTo(1);
            assertThat(features.get(0).geometry()).isNotNull();
            assertThat(features.get(0).geometry().getNumGeometries()).isEqualTo(1);
        }
    }

    @Test
    void region_addFeature_and_getFeature_should_return_correct_coordinates(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("region_coord.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionDataset rd = ds.createRegionDataset("Regions", 4326);
            MultiPolygon geom = buildMultiPolygon(10.0, 20.0, 30.0, 40.0);
            rd.insert(new RegionFeature(1, geom, Map.of()));

            RegionFeature f = rd.getById(1);
            assertThat(f).isNotNull();
            assertThat(f.id()).isEqualTo(1);

            Polygon poly = (Polygon) f.geometry().getGeometryN(0);
            Coordinate[] coords = poly.getExteriorRing().getCoordinates();
            // 外环第一个点应为 (10.0, 20.0)
            assertThat(coords[0].x).isEqualTo(10.0);
            assertThat(coords[0].y).isEqualTo(20.0);
        }
    }

    @Test
    void region_getFeature_nonexistent_should_return_null(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("region_null.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionDataset rd = ds.createRegionDataset("Regions", 4326);
            assertThat(rd.getById(999)).isNull();
        }
    }

    @Test
    void region_deleteFeature_should_remove_feature_and_decrement_count(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("region_delete.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionDataset rd = ds.createRegionDataset("Regions", 4326);
            rd.insert(new RegionFeature(1, buildMultiPolygon(0, 0, 1, 1), Map.of()));
            rd.insert(new RegionFeature(2, buildMultiPolygon(2, 2, 3, 3), Map.of()));

            assertThat(rd.list()).hasSize(2);

            rd.delete(1);

            assertThat(rd.list()).hasSize(1);
            assertThat(rd.getById(1)).isNull();
            assertThat(rd.getById(2)).isNotNull();
        }

        // 重新打开验证 ObjectCount 已递减
        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            RegionDataset rd = (RegionDataset) ds.getDataset("Regions");
            assertThat(rd.getObjectCount()).isEqualTo(1);
        }
    }

    @Test
    void region_deleteFeature_nonexistent_should_throw(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("region_delete_none.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionDataset rd = ds.createRegionDataset("Regions", 4326);
            rd.insert(new RegionFeature(1, buildMultiPolygon(0, 0, 1, 1), Map.of()));

            assertThatThrownBy(() -> rd.delete(999))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void region_updateFeature_should_change_geometry_and_attributes(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("region_update.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "NAME", FieldType.NTEXT, "名称", false)
        );

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionDataset rd = ds.createRegionDataset("Regions", 4326, fields);
            rd.insert(new RegionFeature(1, buildMultiPolygon(0, 0, 1, 1), Map.of("NAME", "OldName")));

            rd.update(1, buildMultiPolygon(5, 5, 10, 10), Map.of("NAME", "NewName"));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            RegionDataset rd = (RegionDataset) ds.getDataset("Regions");
            RegionFeature f = rd.getById(1);
            assertThat(f).isNotNull();

            Polygon poly = (Polygon) f.geometry().getGeometryN(0);
            Coordinate[] coords = poly.getExteriorRing().getCoordinates();
            // 更新后外环第一个点应为 (5.0, 5.0)
            assertThat(coords[0].x).isEqualTo(5.0);
            assertThat(coords[0].y).isEqualTo(5.0);
            assertThat(f.attributes()).containsEntry("NAME", "NewName");
        }
    }

    @Test
    void region_updateFeature_nonexistent_should_throw(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("region_update_none.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionDataset rd = ds.createRegionDataset("Regions", 4326);
            rd.insert(new RegionFeature(1, buildMultiPolygon(0, 0, 1, 1), Map.of()));

            assertThatThrownBy(() -> rd.update(999, buildMultiPolygon(0, 0, 1, 1), Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void region_addFeature_with_attributes_should_persist(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("region_attr.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "AREA", FieldType.DOUBLE, "面积", false),
                new FieldInfo(0, "LABEL", FieldType.NTEXT, "标签", false)
        );

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionDataset rd = ds.createRegionDataset("Regions", 4326, fields);
            rd.insert(new RegionFeature(1, buildMultiPolygon(0, 0, 1, 1),
                    Map.of("AREA", 1.5, "LABEL", "TestArea")));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            RegionDataset rd = (RegionDataset) ds.getDataset("Regions");
            RegionFeature f = rd.getById(1);
            assertThat(f.attributes()).containsEntry("LABEL", "TestArea");
        }
    }

    // ── LineDataset 测试 ──────────────────────────────────────────────────────

    @Test
    void line_addFeature_and_getFeatures_should_work(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("line.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineDataset ld = ds.createLineDataset("Lines", 4326);
            MultiLineString geom = buildMultiLineString(0.0, 0.0, 1.0, 1.0);
            ld.insert(new LineFeature(1, geom, Map.of()));

            List<LineFeature> features = ld.list();
            assertThat(features).hasSize(1);
            assertThat(features.get(0).id()).isEqualTo(1);
            assertThat(features.get(0).geometry()).isNotNull();
        }
    }

    @Test
    void line_addFeature_and_getFeature_should_return_correct_coordinates(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("line_coord.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineDataset ld = ds.createLineDataset("Lines", 4326);
            MultiLineString geom = buildMultiLineString(1.0, 2.0, 3.0, 4.0);
            ld.insert(new LineFeature(1, geom, Map.of()));

            LineFeature f = ld.getById(1);
            assertThat(f).isNotNull();
            assertThat(f.id()).isEqualTo(1);

            // 验证 MultiLineString 坐标
            LineString ls = (LineString) f.geometry().getGeometryN(0);
            Coordinate[] coords = ls.getCoordinates();
            assertThat(coords[0].x).isEqualTo(1.0);
            assertThat(coords[0].y).isEqualTo(2.0);
            assertThat(coords[1].x).isEqualTo(3.0);
            assertThat(coords[1].y).isEqualTo(4.0);
        }
    }

    @Test
    void line_getFeature_nonexistent_should_return_null(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("line_null.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineDataset ld = ds.createLineDataset("Lines", 4326);
            assertThat(ld.getById(999)).isNull();
        }
    }

    @Test
    void line_deleteFeature_should_remove_feature_and_decrement_count(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("line_delete.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineDataset ld = ds.createLineDataset("Lines", 4326);
            ld.insert(new LineFeature(1, buildMultiLineString(0, 0, 1, 1), Map.of()));
            ld.insert(new LineFeature(2, buildMultiLineString(2, 2, 3, 3), Map.of()));

            assertThat(ld.list()).hasSize(2);

            ld.delete(1);

            assertThat(ld.list()).hasSize(1);
            assertThat(ld.getById(1)).isNull();
            assertThat(ld.getById(2)).isNotNull();
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            LineDataset ld = (LineDataset) ds.getDataset("Lines");
            assertThat(ld.getObjectCount()).isEqualTo(1);
        }
    }

    @Test
    void line_deleteFeature_nonexistent_should_throw(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("line_delete_none.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineDataset ld = ds.createLineDataset("Lines", 4326);
            ld.insert(new LineFeature(1, buildMultiLineString(0, 0, 1, 1), Map.of()));

            assertThatThrownBy(() -> ld.delete(999))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void line_updateFeature_should_change_geometry(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("line_update.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineDataset ld = ds.createLineDataset("Lines", 4326);
            ld.insert(new LineFeature(1, buildMultiLineString(0, 0, 1, 1), Map.of()));

            ld.update(1, buildMultiLineString(5, 6, 7, 8), Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            LineDataset ld = (LineDataset) ds.getDataset("Lines");
            LineFeature f = ld.getById(1);
            assertThat(f).isNotNull();

            LineString ls = (LineString) f.geometry().getGeometryN(0);
            Coordinate[] coords = ls.getCoordinates();
            assertThat(coords[0].x).isEqualTo(5.0);
            assertThat(coords[0].y).isEqualTo(6.0);
            assertThat(coords[1].x).isEqualTo(7.0);
            assertThat(coords[1].y).isEqualTo(8.0);
        }
    }

    @Test
    void line_updateFeature_with_attributes_should_persist(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("line_update_attr.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "ROAD", FieldType.NTEXT, "道路名", false)
        );

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineDataset ld = ds.createLineDataset("Lines", 4326, fields);
            ld.insert(new LineFeature(1, buildMultiLineString(0, 0, 1, 1), Map.of("ROAD", "OldRoad")));

            ld.update(1, null, Map.of("ROAD", "NewRoad"));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            LineDataset ld = (LineDataset) ds.getDataset("Lines");
            LineFeature f = ld.getById(1);
            assertThat(f.attributes()).containsEntry("ROAD", "NewRoad");
        }
    }

    @Test
    void line_updateFeature_nonexistent_should_throw(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("line_update_none.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineDataset ld = ds.createLineDataset("Lines", 4326);
            ld.insert(new LineFeature(1, buildMultiLineString(0, 0, 1, 1), Map.of()));

            assertThatThrownBy(() -> ld.update(999, buildMultiLineString(0, 0, 1, 1), Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    // ── TabularDataset 测试 ───────────────────────────────────────────────────

    @Test
    void tabular_addRow_and_getRecords_should_work(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("tabular.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "NAME", FieldType.NTEXT, "名称", false)
        );

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            TabularDataset td = ds.createTabularDataset("Data", fields);
            td.insert(new TabularRecord(1, Map.of("NAME", "Alice")));
            td.insert(new TabularRecord(2, Map.of("NAME", "Bob")));

            List<TabularRecord> records = td.list();
            assertThat(records).hasSize(2);
        }
    }

    @Test
    void tabular_addRow_and_getRecord_should_return_correct_data(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("tabular_record.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "CITY", FieldType.NTEXT, "城市", false),
                new FieldInfo(0, "POP", FieldType.INT32, "人口", false)
        );

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            TabularDataset td = ds.createTabularDataset("Cities", fields);
            td.insert(new TabularRecord(1, Map.of("CITY", "Beijing", "POP", 21000000)));

            TabularRecord r = td.getById(1);
            assertThat(r).isNotNull();
            assertThat(r.id()).isEqualTo(1);
            assertThat(r.attributes()).containsEntry("CITY", "Beijing");
            assertThat(r.attributes()).containsEntry("POP", 21000000);
        }
    }

    @Test
    void tabular_getRecord_nonexistent_should_return_null(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("tabular_null.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            TabularDataset td = ds.createTabularDataset("Data");
            assertThat(td.getById(999)).isNull();
        }
    }

    @Test
    void tabular_deleteRow_should_remove_record_and_decrement_count(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("tabular_delete.udbx").toString();
        List<FieldInfo> fields = List.of(new FieldInfo(0, "VAL", FieldType.NTEXT, "值", false));

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            TabularDataset td = ds.createTabularDataset("Data", fields);
            td.insert(new TabularRecord(1, Map.of("VAL", "A")));
            td.insert(new TabularRecord(2, Map.of("VAL", "B")));
            td.insert(new TabularRecord(3, Map.of("VAL", "C")));

            assertThat(td.list()).hasSize(3);

            td.delete(2);

            assertThat(td.list()).hasSize(2);
            assertThat(td.getById(2)).isNull();
            assertThat(td.getById(1)).isNotNull();
            assertThat(td.getById(3)).isNotNull();
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            TabularDataset td = (TabularDataset) ds.getDataset("Data");
            assertThat(td.getObjectCount()).isEqualTo(2);
        }
    }

    @Test
    void tabular_deleteRow_nonexistent_should_throw(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("tabular_delete_none.udbx").toString();
        List<FieldInfo> fields = List.of(new FieldInfo(0, "VAL", FieldType.NTEXT, "值", false));

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            TabularDataset td = ds.createTabularDataset("Data", fields);
            td.insert(new TabularRecord(1, Map.of("VAL", "A")));

            assertThatThrownBy(() -> td.delete(999))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void tabular_updateRow_should_change_attributes(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("tabular_update.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "SCORE", FieldType.INT32, "分数", false),
                new FieldInfo(0, "GRADE", FieldType.NTEXT, "等级", false)
        );

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            TabularDataset td = ds.createTabularDataset("Students", fields);
            td.insert(new TabularRecord(1, Map.of("SCORE", 60, "GRADE", "C")));

            td.update(1, Map.of("SCORE", 95, "GRADE", "A"));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            TabularDataset td = (TabularDataset) ds.getDataset("Students");
            TabularRecord r = td.getById(1);
            assertThat(r).isNotNull();
            assertThat(r.attributes()).containsEntry("SCORE", 95);
            assertThat(r.attributes()).containsEntry("GRADE", "A");
        }
    }

    @Test
    void tabular_updateRow_nonexistent_should_throw(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("tabular_update_none.udbx").toString();
        List<FieldInfo> fields = List.of(new FieldInfo(0, "VAL", FieldType.NTEXT, "值", false));

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            TabularDataset td = ds.createTabularDataset("Data", fields);
            td.insert(new TabularRecord(1, Map.of("VAL", "A")));

            assertThatThrownBy(() -> td.update(999, Map.of("VAL", "X")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void tabular_updateRow_with_empty_attributes_should_not_throw(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("tabular_update_empty.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            TabularDataset td = ds.createTabularDataset("Data");
            td.insert(new TabularRecord(1, Map.of()));

            // update with null/empty should be a no-op (no exception)
            td.update(1, null);
            td.update(1, Map.of());
        }
    }

    // ── CadDataset 测试 ───────────────────────────────────────────────────────

    @Test
    void cad_addFeature_geoPoint_and_getFeatures_should_work(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad_point.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            CadDataset cd = ds.createCadDataset("CAD");
            CadGeometry.GeoPoint geom = new CadGeometry.GeoPoint(3.0, 4.0, defaultStyleMarker());
            cd.insert(new CadFeature(1, geom, Map.of()));

            List<CadFeature> features = cd.list();
            assertThat(features).hasSize(1);
            assertThat(features.get(0).id()).isEqualTo(1);
            assertThat(features.get(0).geometry()).isInstanceOf(CadGeometry.GeoPoint.class);

            CadGeometry.GeoPoint readBack = (CadGeometry.GeoPoint) features.get(0).geometry();
            assertThat(readBack.x()).isEqualTo(3.0);
            assertThat(readBack.y()).isEqualTo(4.0);
        }
    }

    @Test
    void cad_addFeature_geoLine_and_getFeature_should_work(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad_line.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            CadDataset cd = ds.createCadDataset("CAD");
            CadGeometry.GeoLine geom = new CadGeometry.GeoLine(
                    1,
                    new int[]{3},
                    new double[]{0.0, 1.0, 2.0},
                    new double[]{0.0, 1.0, 0.0},
                    defaultStyleLine()
            );
            cd.insert(new CadFeature(1, geom, Map.of()));

            CadFeature f = cd.getById(1);
            assertThat(f).isNotNull();
            assertThat(f.geometry()).isInstanceOf(CadGeometry.GeoLine.class);

            CadGeometry.GeoLine readBack = (CadGeometry.GeoLine) f.geometry();
            assertThat(readBack.numSub()).isEqualTo(1);
            assertThat(readBack.xs()[0]).isEqualTo(0.0);
            assertThat(readBack.ys()[0]).isEqualTo(0.0);
        }
    }

    @Test
    void cad_addFeature_geoRegion_and_getFeature_should_work(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad_region.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            CadDataset cd = ds.createCadDataset("CAD");
            // 面：1个子对象，5个点（闭合环）
            CadGeometry.GeoRegion geom = new CadGeometry.GeoRegion(
                    1,
                    new int[]{5},
                    new double[]{0.0, 1.0, 1.0, 0.0, 0.0},
                    new double[]{0.0, 0.0, 1.0, 1.0, 0.0},
                    defaultStyleFill()
            );
            cd.insert(new CadFeature(1, geom, Map.of()));

            CadFeature f = cd.getById(1);
            assertThat(f).isNotNull();
            assertThat(f.geometry()).isInstanceOf(CadGeometry.GeoRegion.class);

            CadGeometry.GeoRegion readBack = (CadGeometry.GeoRegion) f.geometry();
            assertThat(readBack.numSub()).isEqualTo(1);
        }
    }

    @Test
    void cad_getFeature_nonexistent_should_return_null(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad_null.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            CadDataset cd = ds.createCadDataset("CAD");
            assertThat(cd.getById(999)).isNull();
        }
    }

    @Test
    void cad_deleteFeature_should_remove_feature_and_decrement_count(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad_delete.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            CadDataset cd = ds.createCadDataset("CAD");
            cd.insert(new CadFeature(1, new CadGeometry.GeoPoint(1.0, 2.0, defaultStyleMarker()), Map.of()));
            cd.insert(new CadFeature(2, new CadGeometry.GeoPoint(3.0, 4.0, defaultStyleMarker()), Map.of()));
            cd.insert(new CadFeature(3, new CadGeometry.GeoPoint(5.0, 6.0, defaultStyleMarker()), Map.of()));

            assertThat(cd.list()).hasSize(3);

            cd.delete(2);

            assertThat(cd.list()).hasSize(2);
            assertThat(cd.getById(2)).isNull();
            assertThat(cd.getById(1)).isNotNull();
            assertThat(cd.getById(3)).isNotNull();
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            CadDataset cd = (CadDataset) ds.getDataset("CAD");
            assertThat(cd.getObjectCount()).isEqualTo(2);
        }
    }

    @Test
    void cad_deleteFeature_nonexistent_should_throw(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad_delete_none.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            CadDataset cd = ds.createCadDataset("CAD");
            cd.insert(new CadFeature(1, new CadGeometry.GeoPoint(1.0, 2.0, defaultStyleMarker()), Map.of()));

            assertThatThrownBy(() -> cd.delete(999))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void cad_updateFeature_should_change_geometry(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad_update.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            CadDataset cd = ds.createCadDataset("CAD");
            cd.insert(new CadFeature(1, new CadGeometry.GeoPoint(1.0, 2.0, defaultStyleMarker()), Map.of()));

            CadGeometry.GeoPoint newGeom = new CadGeometry.GeoPoint(10.0, 20.0, defaultStyleMarker());
            cd.update(1, newGeom, Map.of());
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            CadDataset cd = (CadDataset) ds.getDataset("CAD");
            CadFeature f = cd.getById(1);
            assertThat(f).isNotNull();
            assertThat(f.geometry()).isInstanceOf(CadGeometry.GeoPoint.class);

            CadGeometry.GeoPoint readBack = (CadGeometry.GeoPoint) f.geometry();
            assertThat(readBack.x()).isEqualTo(10.0);
            assertThat(readBack.y()).isEqualTo(20.0);
        }
    }

    @Test
    void cad_updateFeature_with_attributes_should_persist(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad_update_attr.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "TAG", FieldType.NTEXT, "标签", false)
        );

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            CadDataset cd = ds.createCadDataset("CAD", fields);
            cd.insert(new CadFeature(1, new CadGeometry.GeoPoint(1.0, 2.0, defaultStyleMarker()),
                    Map.of("TAG", "OldTag")));

            cd.update(1, null, Map.of("TAG", "NewTag"));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            CadDataset cd = (CadDataset) ds.getDataset("CAD");
            CadFeature f = cd.getById(1);
            assertThat(f.attributes()).containsEntry("TAG", "NewTag");
        }
    }

    @Test
    void cad_updateFeature_nonexistent_should_throw(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad_update_none.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            CadDataset cd = ds.createCadDataset("CAD");
            cd.insert(new CadFeature(1, new CadGeometry.GeoPoint(1.0, 2.0, defaultStyleMarker()), Map.of()));

            assertThatThrownBy(() -> cd.update(999,
                    new CadGeometry.GeoPoint(0, 0, defaultStyleMarker()), Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void cad_addFeature_geoCircle_and_getFeature_should_work(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad_circle.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            CadDataset cd = ds.createCadDataset("CAD");
            CadGeometry.GeoCircle geom = new CadGeometry.GeoCircle(5.0, 5.0, 3.0, defaultStyleFill());
            cd.insert(new CadFeature(1, geom, Map.of()));

            CadFeature f = cd.getById(1);
            assertThat(f).isNotNull();
            assertThat(f.geometry()).isInstanceOf(CadGeometry.GeoCircle.class);

            CadGeometry.GeoCircle readBack = (CadGeometry.GeoCircle) f.geometry();
            assertThat(readBack.centerX()).isEqualTo(5.0);
            assertThat(readBack.centerY()).isEqualTo(5.0);
            assertThat(readBack.radius()).isEqualTo(3.0);
        }
    }

    @Test
    void cad_multiple_features_getFeatures_should_return_all(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad_multi.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            CadDataset cd = ds.createCadDataset("CAD");
            cd.insert(new CadFeature(1, new CadGeometry.GeoPoint(1.0, 1.0, defaultStyleMarker()), Map.of()));
            cd.insert(new CadFeature(2, new CadGeometry.GeoCircle(2.0, 2.0, 1.0, defaultStyleFill()), Map.of()));
            cd.insert(new CadFeature(3, new CadGeometry.GeoLine(
                    1, new int[]{2},
                    new double[]{0.0, 1.0}, new double[]{0.0, 1.0},
                    defaultStyleLine()), Map.of()));

            List<CadFeature> features = cd.list();
            assertThat(features).hasSize(3);
        }
    }
}
