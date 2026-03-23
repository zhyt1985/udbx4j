package com.supermap.udbx.integration;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.dataset.CadDataset;
import com.supermap.udbx.dataset.CadFeature;
import com.supermap.udbx.geometry.cad.CadGeometry;
import com.supermap.udbx.geometry.cad.CadGeometryReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：从 SampleData.udbx 的 CADDT 数据集读取并解码 CAD 几何对象。
 */
class CadDatasetReadTest {

    private static final String SAMPLE_DATA = "src/test/resources/SampleData.udbx";
    private static final Path SAMPLE_DATA_PATH = Paths.get(SAMPLE_DATA);

    @Test
    void caddt_must_have_92_objects() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA_PATH);
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM CADDT");
             ResultSet rs = stmt.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(92);
        }
    }

    @Test
    void caddt_must_decode_all_blobs_without_exception() throws Exception {
        List<String> failures = new ArrayList<>();
        int count = 0;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA_PATH);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT SmID, SmGeometry FROM CADDT ORDER BY SmID");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int smId = rs.getInt("SmID");
                byte[] blob = rs.getBytes("SmGeometry");
                try {
                    CadGeometryReader.read(blob);
                    count++;
                } catch (Exception e) {
                    failures.add("SmID=" + smId + ": " + e.getMessage());
                }
            }
        }

        assertThat(failures)
                .withFailMessage("Failed to decode %d blobs:\n%s",
                        failures.size(), String.join("\n", failures))
                .isEmpty();
        assertThat(count).isEqualTo(92);
    }

    @Test
    void caddt_must_contain_multiple_geo_types() throws Exception {
        Map<String, Integer> typeCounts = new HashMap<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA_PATH);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT SmGeometry FROM CADDT ORDER BY SmID");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                byte[] blob = rs.getBytes("SmGeometry");
                CadGeometry geom = CadGeometryReader.read(blob);
                String typeName = geom.getClass().getSimpleName();
                typeCounts.merge(typeName, 1, Integer::sum);
            }
        }

        System.out.println("CAD geometry type distribution: " + typeCounts);

        // CADDT 应包含多种几何类型
        assertThat(typeCounts).isNotEmpty();
        assertThat(typeCounts.size()).isGreaterThanOrEqualTo(2);
    }

    // ── CadDataset 高级 API 测试 ──────────────────────────────────────────────

    @Test
    void caddataset_getfeatures_must_return_92_features() {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DATA)) {
            CadDataset dataset = (CadDataset) ds.getDataset("CADDT");
            List<CadFeature> features = dataset.getFeatures();
            assertThat(features).hasSize(92);
        }
    }

    @Test
    void caddataset_getfeature_must_return_correct_geometry_type() {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DATA)) {
            CadDataset dataset = (CadDataset) ds.getDataset("CADDT");
            // SmID=1 已知为 GeoLine（根据之前集成测试数据分布）
            CadFeature feature = dataset.getFeature(1);
            assertThat(feature).isNotNull();
            assertThat(feature.smId()).isEqualTo(1);
            assertThat(feature.geometry()).isNotNull();
        }
    }

    @Test
    void caddataset_getfeature_nonexistent_must_return_null() {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DATA)) {
            CadDataset dataset = (CadDataset) ds.getDataset("CADDT");
            CadFeature feature = dataset.getFeature(99999);
            assertThat(feature).isNull();
        }
    }

    @Test
    void caddataset_features_must_have_4_geo_subtypes() {
        try (UdbxDataSource ds = UdbxDataSource.open(SAMPLE_DATA)) {
            CadDataset dataset = (CadDataset) ds.getDataset("CADDT");
            List<CadFeature> features = dataset.getFeatures();

            Map<String, Long> typeCounts = new HashMap<>();
            for (CadFeature f : features) {
                String typeName = f.geometry().getClass().getSimpleName();
                typeCounts.merge(typeName, 1L, Long::sum);
            }
            System.out.println("CadDataset geometry type distribution: " + typeCounts);

            // GeoLine=47, GeoPoint=15, GeoRegion=15, GeoPoint3D=15
            assertThat(typeCounts).containsKey("GeoLine");
            assertThat(typeCounts).containsKey("GeoPoint");
            assertThat(typeCounts).containsKey("GeoRegion");
            assertThat(typeCounts.get("GeoLine")).isEqualTo(47L);
        }
    }
}
