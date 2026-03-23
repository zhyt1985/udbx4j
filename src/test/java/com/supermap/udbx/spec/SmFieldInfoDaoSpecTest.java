package com.supermap.udbx.spec;

import com.supermap.udbx.core.FieldInfo;
import com.supermap.udbx.core.FieldType;
import com.supermap.udbx.system.SmFieldInfoDao;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 测试：验证 SmFieldInfoDao 能够正确读取字段元信息。
 *
 * <p>对应白皮书 §2.2 表 10 SmFieldInfo 字段定义。
 *
 * <p>测试数据（SampleData.udbx 中 BaseMap_P 的已知字段）：
 * <pre>
 *   SmID:       FieldType=4 (Int32),    Required=true
 *   NAME:       FieldType=127 (NText),  Required=false
 *   CODE:       FieldType=4 (Int32),    Required=false
 *   SmGeometry: FieldType=128 (Text),   Required=true
 * </pre>
 */
class SmFieldInfoDaoSpecTest {

    private static final Path SAMPLE_DATA = Paths.get("src/test/resources/SampleData.udbx");

    @Test
    void should_find_fields_by_dataset_id() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmFieldInfoDao dao = new SmFieldInfoDao(conn);

            List<FieldInfo> fields = dao.findByDatasetId(1); // BaseMap_P

            // BaseMap_P 至少有 5 个字段：SmID, SmUserID, SmGeometry, NAME, CODE
            assertThat(fields).hasSizeGreaterThanOrEqualTo(5);
        }
    }

    @Test
    void should_find_name_field_with_ntext_type() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmFieldInfoDao dao = new SmFieldInfoDao(conn);

            Optional<FieldInfo> result = dao.findByDatasetIdAndFieldName(1, "NAME");

            assertThat(result).isPresent();
            FieldInfo field = result.get();
            assertThat(field.datasetId()).isEqualTo(1);
            assertThat(field.fieldName()).isEqualTo("NAME");
            assertThat(field.fieldType()).isEqualTo(FieldType.NText);
            assertThat(field.fieldAlias()).isEqualTo("NAME"); // SmFieldCaption
            assertThat(field.required()).isFalse(); // SmFieldbRequired=0
        }
    }

    @Test
    void should_find_code_field_with_int32_type() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmFieldInfoDao dao = new SmFieldInfoDao(conn);

            Optional<FieldInfo> result = dao.findByDatasetIdAndFieldName(1, "CODE");

            assertThat(result).isPresent();
            FieldInfo field = result.get();
            assertThat(field.fieldType()).isEqualTo(FieldType.Int32);
            assertThat(field.required()).isFalse();
        }
    }

    @Test
    void should_find_smid_field_with_required_true() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmFieldInfoDao dao = new SmFieldInfoDao(conn);

            Optional<FieldInfo> result = dao.findByDatasetIdAndFieldName(1, "SmID");

            assertThat(result).isPresent();
            FieldInfo field = result.get();
            assertThat(field.fieldType()).isEqualTo(FieldType.Int32);
            assertThat(field.required()).isTrue(); // SmFieldbRequired=1
        }
    }

    @Test
    void should_find_smgeometry_field_with_text_type() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmFieldInfoDao dao = new SmFieldInfoDao(conn);

            Optional<FieldInfo> result = dao.findByDatasetIdAndFieldName(1, "SmGeometry");

            assertThat(result).isPresent();
            FieldInfo field = result.get();
            assertThat(field.fieldType()).isEqualTo(FieldType.Text);
            assertThat(field.required()).isTrue();
        }
    }

    @Test
    void should_return_empty_for_nonexistent_field() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmFieldInfoDao dao = new SmFieldInfoDao(conn);

            Optional<FieldInfo> result = dao.findByDatasetIdAndFieldName(1, "NonExistent");

            assertThat(result).isEmpty();
        }
    }

    @Test
    void should_find_fields_for_line_dataset() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmFieldInfoDao dao = new SmFieldInfoDao(conn);

            List<FieldInfo> fields = dao.findByDatasetId(2); // BaseMap_L

            // BaseMap_L 有 SmID, SmUserID, SmLength, SmTopoError, SmGeometry
            assertThat(fields).hasSizeGreaterThanOrEqualTo(5);

            // 验证有 SmLength 字段
            Optional<FieldInfo> lengthField = fields.stream()
                    .filter(f -> f.fieldName().equals("SmLength"))
                    .findFirst();
            assertThat(lengthField).isPresent();
            assertThat(lengthField.get().fieldType()).isEqualTo(FieldType.Double);
        }
    }

    @Test
    void should_find_fields_for_region_dataset() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SAMPLE_DATA)) {
            SmFieldInfoDao dao = new SmFieldInfoDao(conn);

            List<FieldInfo> fields = dao.findByDatasetId(3); // BaseMap_R

            // BaseMap_R 应该有 SmID, SmUserID, SmGeometry, NAME, CODE, SmArea, SmPerimeter
            assertThat(fields).hasSizeGreaterThanOrEqualTo(7);

            // 验证有 SmArea 和 SmPerimeter 字段
            assertThat(fields.stream().anyMatch(f -> f.fieldName().equals("SmArea"))).isTrue();
            assertThat(fields.stream().anyMatch(f -> f.fieldName().equals("SmPerimeter"))).isTrue();
        }
    }
}
