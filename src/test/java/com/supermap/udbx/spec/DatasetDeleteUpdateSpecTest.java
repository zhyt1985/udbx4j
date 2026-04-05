package com.supermap.udbx.spec;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.core.FieldInfo;
import com.supermap.udbx.core.FieldType;
import com.supermap.udbx.dataset.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 测试：数据集删除与更新操作。
 *
 * <p>规范：
 * <ul>
 *   <li>deleteFeature(smId) - 删除指定要素，SmObjectCount 同步递减</li>
 *   <li>updateFeature(smId, geometry, attributes) - 更新几何和属性</li>
 *   <li>deleteRow(smId) - TabularDataset 专用删除</li>
 *   <li>updateRow(smId, attributes) - TabularDataset 专用更新</li>
 * </ul>
 */
class DatasetDeleteUpdateSpecTest {

    private static final GeometryFactory GF = new GeometryFactory();

    @Test
    void deleteFeature_should_remove_point_and_decrement_count(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("delete.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointDataset pd = ds.createPointDataset("Points", 4326);
            pd.insert(new PointFeature(1, GF.createPoint(new Coordinate(1, 2)), Map.of()));
            pd.insert(new PointFeature(2, GF.createPoint(new Coordinate(3, 4)), Map.of()));
            pd.insert(new PointFeature(3, GF.createPoint(new Coordinate(5, 6)), Map.of()));

            assertThat(pd.list()).hasSize(3);

            // 删除 SmID=2
            pd.delete(2);

            assertThat(pd.list()).hasSize(2);
            assertThat(pd.getById(2)).isNull();
            assertThat(pd.list()).hasSize(2);
        }

        // 重新打开验证持久化
        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            PointDataset pd = (PointDataset) ds.getDataset("Points");
            assertThat(pd.getObjectCount()).isEqualTo(2);
            assertThat(pd.getById(2)).isNull();
            assertThat(pd.getById(1)).isNotNull();
            assertThat(pd.getById(3)).isNotNull();
        }
    }

    @Test
    void deleteFeature_nonexistent_should_throw(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("delete_none.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointDataset pd = ds.createPointDataset("Points", 4326);
            pd.insert(new PointFeature(1, GF.createPoint(new Coordinate(1, 2)), Map.of()));

            assertThatThrownBy(() -> pd.delete(999))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void updateFeature_should_change_geometry_and_attributes(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("update.udbx").toString();
        List<FieldInfo> fields = List.of(
                new FieldInfo(0, "NAME", FieldType.NTEXT, "名称", false),
                new FieldInfo(0, "VALUE", FieldType.INT32, "数值", false)
        );

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointDataset pd = ds.createPointDataset("Points", 4326, fields);
            pd.insert(new PointFeature(1, GF.createPoint(new Coordinate(1, 2)),
                    Map.of("NAME", "OldName", "VALUE", 100)));

            // 更新
            pd.update(1, GF.createPoint(new Coordinate(10, 20)),
                    Map.of("NAME", "NewName", "VALUE", 200));
        }

        // 验证更新结果
        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            PointDataset pd = (PointDataset) ds.getDataset("Points");
            PointFeature f = pd.getById(1);

            assertThat(f.geometry().getX()).isEqualTo(10.0);
            assertThat(f.geometry().getY()).isEqualTo(20.0);
            assertThat(f.attributes()).containsEntry("NAME", "NewName");
            assertThat(f.attributes()).containsEntry("VALUE", 200);
        }
    }

    @Test
    void updateFeature_nonexistent_should_throw(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("update_none.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            PointDataset pd = ds.createPointDataset("Points", 4326);
            pd.insert(new PointFeature(1, GF.createPoint(new Coordinate(1, 2)), Map.of()));

            assertThatThrownBy(() -> pd.update(999, GF.createPoint(new Coordinate(0, 0)), Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    void tabular_deleteRow_should_work(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("tabular_delete.udbx").toString();
        List<FieldInfo> fields = List.of(new FieldInfo(0, "CITY", FieldType.NTEXT, "城市", false));

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            TabularDataset td = ds.createTabularDataset("Cities", fields);
            td.insert(new TabularRecord(1, Map.of("CITY", "Beijing")));
            td.insert(new TabularRecord(2, Map.of("CITY", "Shanghai")));

            assertThat(td.list()).hasSize(2);

            td.delete(1);

            assertThat(td.list()).hasSize(1);
            assertThat(td.getById(1)).isNull();
            assertThat(td.getById(2)).isNotNull();
        }
    }

    @Test
    void tabular_updateRow_should_work(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("tabular_update.udbx").toString();
        List<FieldInfo> fields = List.of(new FieldInfo(0, "CITY", FieldType.NTEXT, "城市", false));

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            TabularDataset td = ds.createTabularDataset("Cities", fields);
            td.insert(new TabularRecord(1, Map.of("CITY", "OldCity")));

            td.update(1, Map.of("CITY", "NewCity"));
        }

        try (UdbxDataSource ds = UdbxDataSource.open(path)) {
            TabularDataset td = (TabularDataset) ds.getDataset("Cities");
            TabularRecord r = td.getById(1);
            assertThat(r.attributes()).containsEntry("CITY", "NewCity");
        }
    }

    @Test
    void cad_deleteFeature_should_work(@TempDir Path tmpDir) {
        String path = tmpDir.resolve("cad_delete.udbx").toString();

        try (UdbxDataSource ds = UdbxDataSource.create(path)) {
            CadDataset cd = ds.createCadDataset("CAD");
            cd.insert(new CadFeature(1, new com.supermap.udbx.geometry.cad.CadGeometry.GeoPoint(
                    1.0, 2.0, new com.supermap.udbx.geometry.cad.StyleMarker(
                    0, 0, 0, 0, 0, 0, (byte) 0, (byte) 0, (short) 0, (short) 0, (short) 0, 0)), Map.of()));
            cd.insert(new CadFeature(2, new com.supermap.udbx.geometry.cad.CadGeometry.GeoPoint(
                    3.0, 4.0, new com.supermap.udbx.geometry.cad.StyleMarker(
                    0, 0, 0, 0, 0, 0, (byte) 0, (byte) 0, (short) 0, (short) 0, (short) 0, 0)), Map.of()));

            assertThat(cd.list()).hasSize(2);

            cd.delete(1);

            assertThat(cd.list()).hasSize(1);
            assertThat(cd.getById(1)).isNull();
        }
    }
}
