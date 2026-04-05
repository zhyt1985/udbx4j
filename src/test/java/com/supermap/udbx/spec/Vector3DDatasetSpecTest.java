package com.supermap.udbx.spec;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.core.FieldInfo;
import com.supermap.udbx.core.FieldType;
import com.supermap.udbx.dataset.LineFeature;
import com.supermap.udbx.dataset.LineZDataset;
import com.supermap.udbx.dataset.PointFeature;
import com.supermap.udbx.dataset.PointZDataset;
import com.supermap.udbx.dataset.RegionFeature;
import com.supermap.udbx.dataset.RegionZDataset;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

/**
 * Spec 测试：三维矢量数据集（PointZDataset / LineZDataset / RegionZDataset）的 CRUD 操作。
 *
 * <p>覆盖：addFeature、getFeatures、getFeature（含 Z 坐标验证）、
 * deleteFeature（SmObjectCount 递减）、deleteFeature 不存在时异常、
 * updateFeature（几何 + 属性）、updateFeature 不存在时异常、getObjectCount。
 */
class Vector3DDatasetSpecTest {

    private static final GeometryFactory GF = new GeometryFactory();

    // ========================================================================
    // PointZDataset 测试
    // ========================================================================

    @Test
    void pointz_addFeature_and_getFeatures_preserves_xyz(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("pointz_add.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointZDataset pd = ds.createPointZDataset("PointsZ", 4326);
            pd.insert(new PointFeature(1, GF.createPoint(new Coordinate(10.0, 20.0, 30.0)), Map.of()));
            pd.insert(new PointFeature(2, GF.createPoint(new Coordinate(11.0, 21.0, 31.0)), Map.of()));

            List<PointFeature> features = pd.list();
            assertThat(features).hasSize(2);

            PointFeature f1 = features.get(0);
            assertThat(f1.id()).isEqualTo(1);
            assertThat(f1.geometry().getX()).isCloseTo(10.0, offset(1e-9));
            assertThat(f1.geometry().getY()).isCloseTo(20.0, offset(1e-9));
            assertThat(f1.geometry().getCoordinate().getZ()).isCloseTo(30.0, offset(1e-9));
        }
    }

    @Test
    void pointz_getFeature_by_smId_returns_correct_feature(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("pointz_get.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointZDataset pd = ds.createPointZDataset("PZ", 4326);
            pd.insert(new PointFeature(1, GF.createPoint(new Coordinate(1.0, 2.0, 10.0)), Map.of()));
            pd.insert(new PointFeature(2, GF.createPoint(new Coordinate(3.0, 4.0, 20.0)), Map.of()));

            PointFeature f = pd.getById(2);
            assertThat(f).isNotNull();
            assertThat(f.id()).isEqualTo(2);
            assertThat(f.geometry().getX()).isCloseTo(3.0, offset(1e-9));
            assertThat(f.geometry().getY()).isCloseTo(4.0, offset(1e-9));
            assertThat(f.geometry().getCoordinate().getZ()).isCloseTo(20.0, offset(1e-9));
        }
    }

    @Test
    void pointz_getFeature_nonexistent_returns_null(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("pointz_null.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointZDataset pd = ds.createPointZDataset("PZ", 4326);
            pd.insert(new PointFeature(1, GF.createPoint(new Coordinate(1.0, 2.0, 3.0)), Map.of()));

            assertThat(pd.getById(9999)).isNull();
        }
    }

    @Test
    void pointz_deleteFeature_removes_record_and_decrements_objectCount(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("pointz_delete.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointZDataset pd = ds.createPointZDataset("PZ", 4326);
            pd.insert(new PointFeature(1, GF.createPoint(new Coordinate(1.0, 2.0, 3.0)), Map.of()));
            pd.insert(new PointFeature(2, GF.createPoint(new Coordinate(4.0, 5.0, 6.0)), Map.of()));
            pd.insert(new PointFeature(3, GF.createPoint(new Coordinate(7.0, 8.0, 9.0)), Map.of()));

            assertThat(pd.list()).hasSize(3);

            pd.delete(2);

            assertThat(pd.list()).hasSize(2);
            assertThat(pd.getById(2)).isNull();
        }

        // 重新打开验证 SmObjectCount 持久化
        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            PointZDataset pd = (PointZDataset) ds.getDataset("PZ");
            assertThat(pd.getObjectCount()).isEqualTo(2);
        }
    }

    @Test
    void pointz_deleteFeature_nonexistent_throws_exception(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("pointz_del_ex.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointZDataset pd = ds.createPointZDataset("PZ", 4326);
            pd.insert(new PointFeature(1, GF.createPoint(new Coordinate(1.0, 2.0, 3.0)), Map.of()));

            assertThatThrownBy(() -> pd.delete(9999))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void pointz_updateFeature_changes_geometry_and_attributes(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("pointz_update.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "NAME", FieldType.NTEXT, "名称", false),
                new FieldInfo(0, "ELEV", FieldType.DOUBLE, "高程", false)
        );

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointZDataset pd = ds.createPointZDataset("PZ", 4326, fields);
            pd.insert(new PointFeature(1, GF.createPoint(new Coordinate(1.0, 2.0, 3.0)),
                    Map.of("NAME", "OldName", "ELEV", 100.0)));

            pd.update(1, GF.createPoint(new Coordinate(10.0, 20.0, 50.0)),
                    Map.of("NAME", "NewName", "ELEV", 500.0));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            PointZDataset pd = (PointZDataset) ds.getDataset("PZ");
            PointFeature f = pd.getById(1);

            assertThat(f).isNotNull();
            assertThat(f.geometry().getX()).isCloseTo(10.0, offset(1e-9));
            assertThat(f.geometry().getY()).isCloseTo(20.0, offset(1e-9));
            assertThat(f.geometry().getCoordinate().getZ()).isCloseTo(50.0, offset(1e-9));
            assertThat(f.attributes()).containsEntry("NAME", "NewName");
        }
    }

    @Test
    void pointz_updateFeature_nonexistent_throws_exception(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("pointz_upd_ex.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointZDataset pd = ds.createPointZDataset("PZ", 4326);
            pd.insert(new PointFeature(1, GF.createPoint(new Coordinate(1.0, 2.0, 3.0)), Map.of()));

            assertThatThrownBy(() -> pd.update(9999,
                    GF.createPoint(new Coordinate(0.0, 0.0, 0.0)), Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void pointz_getObjectCount_reflects_added_features(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("pointz_count.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointZDataset pd = ds.createPointZDataset("PZ", 4326);
            pd.insert(new PointFeature(1, GF.createPoint(new Coordinate(1.0, 2.0, 3.0)), Map.of()));
            pd.insert(new PointFeature(2, GF.createPoint(new Coordinate(4.0, 5.0, 6.0)), Map.of()));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            PointZDataset pd = (PointZDataset) ds.getDataset("PZ");
            assertThat(pd.getObjectCount()).isEqualTo(2);
        }
    }

    @Test
    void pointz_addFeature_with_attributes(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("pointz_attr.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "LABEL", FieldType.NTEXT, "标签", false)
        );

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointZDataset pd = ds.createPointZDataset("PZ", 4326, fields);
            pd.insert(new PointFeature(1, GF.createPoint(new Coordinate(5.0, 6.0, 7.0)),
                    Map.of("LABEL", "TestPoint")));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            PointZDataset pd = (PointZDataset) ds.getDataset("PZ");
            PointFeature f = pd.getById(1);
            assertThat(f).isNotNull();
            assertThat(f.attributes()).containsEntry("LABEL", "TestPoint");
        }
    }

    // ========================================================================
    // LineZDataset 测试
    // ========================================================================

    @Test
    void linez_addFeature_and_getFeatures_preserves_xyz(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("linez_add.udbx").toString();
        org.locationtech.jts.geom.MultiLineString mls = GF.createMultiLineString(
                new org.locationtech.jts.geom.LineString[]{
                        GF.createLineString(new Coordinate[]{
                                new Coordinate(0.0, 0.0, 10.0),
                                new Coordinate(1.0, 1.0, 20.0),
                                new Coordinate(2.0, 0.0, 30.0)
                        })
                });

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineZDataset ld = ds.createLineZDataset("LinesZ", 4326);
            ld.insert(new LineFeature(1, mls, Map.of()));
            ld.insert(new LineFeature(2, mls, Map.of()));

            List<LineFeature> features = ld.list();
            assertThat(features).hasSize(2);

            LineFeature f1 = features.get(0);
            assertThat(f1.id()).isEqualTo(1);
            // 验证第一个坐标的 Z 值
            assertThat(f1.geometry().getCoordinate().getZ()).isCloseTo(10.0, offset(1e-9));
        }
    }

    @Test
    void linez_getFeature_by_smId_returns_correct_feature(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("linez_get.udbx").toString();
        org.locationtech.jts.geom.MultiLineString mls = GF.createMultiLineString(
                new org.locationtech.jts.geom.LineString[]{
                        GF.createLineString(new Coordinate[]{
                                new Coordinate(5.0, 5.0, 100.0),
                                new Coordinate(6.0, 6.0, 200.0)
                        })
                });

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineZDataset ld = ds.createLineZDataset("LZ", 4326);
            ld.insert(new LineFeature(1, mls, Map.of()));
            ld.insert(new LineFeature(2, mls, Map.of()));

            LineFeature f = ld.getById(2);
            assertThat(f).isNotNull();
            assertThat(f.id()).isEqualTo(2);
            assertThat(f.geometry().getCoordinate().getZ()).isCloseTo(100.0, offset(1e-9));
        }
    }

    @Test
    void linez_getFeature_nonexistent_returns_null(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("linez_null.udbx").toString();
        org.locationtech.jts.geom.MultiLineString mls = GF.createMultiLineString(
                new org.locationtech.jts.geom.LineString[]{
                        GF.createLineString(new Coordinate[]{
                                new Coordinate(0.0, 0.0, 1.0),
                                new Coordinate(1.0, 1.0, 2.0)
                        })
                });

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineZDataset ld = ds.createLineZDataset("LZ", 4326);
            ld.insert(new LineFeature(1, mls, Map.of()));

            assertThat(ld.getById(9999)).isNull();
        }
    }

    @Test
    void linez_deleteFeature_removes_record_and_decrements_objectCount(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("linez_delete.udbx").toString();
        org.locationtech.jts.geom.MultiLineString mls = GF.createMultiLineString(
                new org.locationtech.jts.geom.LineString[]{
                        GF.createLineString(new Coordinate[]{
                                new Coordinate(0.0, 0.0, 1.0),
                                new Coordinate(1.0, 1.0, 2.0)
                        })
                });

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineZDataset ld = ds.createLineZDataset("LZ", 4326);
            ld.insert(new LineFeature(1, mls, Map.of()));
            ld.insert(new LineFeature(2, mls, Map.of()));
            ld.insert(new LineFeature(3, mls, Map.of()));

            assertThat(ld.list()).hasSize(3);

            ld.delete(2);

            assertThat(ld.list()).hasSize(2);
            assertThat(ld.getById(2)).isNull();
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            LineZDataset ld = (LineZDataset) ds.getDataset("LZ");
            assertThat(ld.getObjectCount()).isEqualTo(2);
        }
    }

    @Test
    void linez_deleteFeature_nonexistent_throws_exception(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("linez_del_ex.udbx").toString();
        org.locationtech.jts.geom.MultiLineString mls = GF.createMultiLineString(
                new org.locationtech.jts.geom.LineString[]{
                        GF.createLineString(new Coordinate[]{
                                new Coordinate(0.0, 0.0, 1.0),
                                new Coordinate(1.0, 1.0, 2.0)
                        })
                });

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineZDataset ld = ds.createLineZDataset("LZ", 4326);
            ld.insert(new LineFeature(1, mls, Map.of()));

            assertThatThrownBy(() -> ld.delete(9999))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void linez_updateFeature_changes_geometry_and_attributes(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("linez_update.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "ROAD", FieldType.NTEXT, "道路", false)
        );
        org.locationtech.jts.geom.MultiLineString mlsOld = GF.createMultiLineString(
                new org.locationtech.jts.geom.LineString[]{
                        GF.createLineString(new Coordinate[]{
                                new Coordinate(0.0, 0.0, 1.0),
                                new Coordinate(1.0, 1.0, 2.0)
                        })
                });
        org.locationtech.jts.geom.MultiLineString mlsNew = GF.createMultiLineString(
                new org.locationtech.jts.geom.LineString[]{
                        GF.createLineString(new Coordinate[]{
                                new Coordinate(10.0, 10.0, 100.0),
                                new Coordinate(11.0, 11.0, 200.0)
                        })
                });

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineZDataset ld = ds.createLineZDataset("LZ", 4326, fields);
            ld.insert(new LineFeature(1, mlsOld, Map.of("ROAD", "OldRoad")));
            ld.update(1, mlsNew, Map.of("ROAD", "NewRoad"));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            LineZDataset ld = (LineZDataset) ds.getDataset("LZ");
            LineFeature f = ld.getById(1);

            assertThat(f).isNotNull();
            assertThat(f.geometry().getCoordinate().getZ()).isCloseTo(100.0, offset(1e-9));
            assertThat(f.attributes()).containsEntry("ROAD", "NewRoad");
        }
    }

    @Test
    void linez_updateFeature_nonexistent_throws_exception(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("linez_upd_ex.udbx").toString();
        org.locationtech.jts.geom.MultiLineString mls = GF.createMultiLineString(
                new org.locationtech.jts.geom.LineString[]{
                        GF.createLineString(new Coordinate[]{
                                new Coordinate(0.0, 0.0, 1.0),
                                new Coordinate(1.0, 1.0, 2.0)
                        })
                });

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            LineZDataset ld = ds.createLineZDataset("LZ", 4326);
            ld.insert(new LineFeature(1, mls, Map.of()));

            assertThatThrownBy(() -> ld.update(9999, mls, Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    // ========================================================================
    // RegionZDataset 测试
    // ========================================================================

    private static MultiPolygon buildMultiPolygon3D(double z) {
        LinearRing ring = GF.createLinearRing(new Coordinate[]{
                new Coordinate(0.0, 0.0, z),
                new Coordinate(1.0, 0.0, z),
                new Coordinate(1.0, 1.0, z),
                new Coordinate(0.0, 1.0, z),
                new Coordinate(0.0, 0.0, z)
        });
        Polygon poly = GF.createPolygon(ring);
        return GF.createMultiPolygon(new Polygon[]{poly});
    }

    @Test
    void regionz_addFeature_and_getFeatures_preserves_xyz(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("regionz_add.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionZDataset rd = ds.createRegionZDataset("RegionsZ", 4326);
            rd.insert(new RegionFeature(1, buildMultiPolygon3D(50.0), Map.of()));
            rd.insert(new RegionFeature(2, buildMultiPolygon3D(60.0), Map.of()));

            List<RegionFeature> features = rd.list();
            assertThat(features).hasSize(2);

            RegionFeature f1 = features.get(0);
            assertThat(f1.id()).isEqualTo(1);
            assertThat(f1.geometry().getCoordinate().getZ()).isCloseTo(50.0, offset(1e-9));
        }
    }

    @Test
    void regionz_getFeature_by_smId_returns_correct_feature(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("regionz_get.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionZDataset rd = ds.createRegionZDataset("RZ", 4326);
            rd.insert(new RegionFeature(1, buildMultiPolygon3D(10.0), Map.of()));
            rd.insert(new RegionFeature(2, buildMultiPolygon3D(20.0), Map.of()));

            RegionFeature f = rd.getById(2);
            assertThat(f).isNotNull();
            assertThat(f.id()).isEqualTo(2);
            assertThat(f.geometry().getCoordinate().getZ()).isCloseTo(20.0, offset(1e-9));
        }
    }

    @Test
    void regionz_getFeature_nonexistent_returns_null(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("regionz_null.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionZDataset rd = ds.createRegionZDataset("RZ", 4326);
            rd.insert(new RegionFeature(1, buildMultiPolygon3D(5.0), Map.of()));

            assertThat(rd.getById(9999)).isNull();
        }
    }

    @Test
    void regionz_deleteFeature_removes_record_and_decrements_objectCount(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("regionz_delete.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionZDataset rd = ds.createRegionZDataset("RZ", 4326);
            rd.insert(new RegionFeature(1, buildMultiPolygon3D(5.0), Map.of()));
            rd.insert(new RegionFeature(2, buildMultiPolygon3D(5.0), Map.of()));
            rd.insert(new RegionFeature(3, buildMultiPolygon3D(5.0), Map.of()));

            assertThat(rd.list()).hasSize(3);

            rd.delete(2);

            assertThat(rd.list()).hasSize(2);
            assertThat(rd.getById(2)).isNull();
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            RegionZDataset rd = (RegionZDataset) ds.getDataset("RZ");
            assertThat(rd.getObjectCount()).isEqualTo(2);
        }
    }

    @Test
    void regionz_deleteFeature_nonexistent_throws_exception(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("regionz_del_ex.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionZDataset rd = ds.createRegionZDataset("RZ", 4326);
            rd.insert(new RegionFeature(1, buildMultiPolygon3D(5.0), Map.of()));

            assertThatThrownBy(() -> rd.delete(9999))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void regionz_updateFeature_changes_geometry_and_attributes(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("regionz_update.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "AREA_NAME", FieldType.NTEXT, "区域名", false)
        );

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionZDataset rd = ds.createRegionZDataset("RZ", 4326, fields);
            rd.insert(new RegionFeature(1, buildMultiPolygon3D(5.0), Map.of("AREA_NAME", "OldArea")));
            rd.update(1, buildMultiPolygon3D(99.0), Map.of("AREA_NAME", "NewArea"));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            RegionZDataset rd = (RegionZDataset) ds.getDataset("RZ");
            RegionFeature f = rd.getById(1);

            assertThat(f).isNotNull();
            assertThat(f.geometry().getCoordinate().getZ()).isCloseTo(99.0, offset(1e-9));
            assertThat(f.attributes()).containsEntry("AREA_NAME", "NewArea");
        }
    }

    @Test
    void regionz_updateFeature_nonexistent_throws_exception(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("regionz_upd_ex.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionZDataset rd = ds.createRegionZDataset("RZ", 4326);
            rd.insert(new RegionFeature(1, buildMultiPolygon3D(5.0), Map.of()));

            assertThatThrownBy(() -> rd.update(9999, buildMultiPolygon3D(0.0), Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void regionz_getObjectCount_reflects_added_features(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("regionz_count.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            RegionZDataset rd = ds.createRegionZDataset("RZ", 4326);
            rd.insert(new RegionFeature(1, buildMultiPolygon3D(5.0), Map.of()));
            rd.insert(new RegionFeature(2, buildMultiPolygon3D(5.0), Map.of()));
            rd.insert(new RegionFeature(3, buildMultiPolygon3D(5.0), Map.of()));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            RegionZDataset rd = (RegionZDataset) ds.getDataset("RZ");
            assertThat(rd.getObjectCount()).isEqualTo(3);
        }
    }
}
