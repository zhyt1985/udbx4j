package com.supermap.udbx.spec;

import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.core.DatasetType;
import com.supermap.udbx.core.FieldInfo;
import com.supermap.udbx.core.FieldType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 测试：验证核心 Record 数据类的字段、不可变性和校验逻辑。
 *
 * <p>对应白皮书章节：
 * <ul>
 *   <li>§2.1 表 5  — SmRegister 字段定义 → DatasetInfo</li>
 *   <li>§2.2 表 10 — SmFieldInfo 字段定义 → FieldInfo</li>
 * </ul>
 *
 * <p>DatasetInfo 核心字段（SmRegister）：
 * <pre>
 *   SmDatasetID     int     数据集 ID
 *   SmDatasetName   String  数据集名称
 *   SmDatasetType   int     数据集类型（→ DatasetType）
 *   SmObjectCount   int     要素数量
 *   SmSRID          int     坐标系 ID
 * </pre>
 *
 * <p>FieldInfo 核心字段（SmFieldInfo）：
 * <pre>
 *   SmDatasetID     int        所属数据集 ID
 *   SmFieldName     String     字段名
 *   SmFieldType     int        字段类型（→ FieldType）
 *   SmFieldAlias    String     字段别名
 *   SmIsRequired    boolean    是否必填
 * </pre>
 */
class CoreRecordsSpecTest {

    // ── DatasetInfo ────────────────────────────────────────────────────────

    @Test
    void dataset_info_must_expose_all_fields() {
        DatasetInfo info = new DatasetInfo(1, "BaseMap_P", DatasetType.Point, 15, 4326);

        assertThat(info.datasetId()).isEqualTo(1);
        assertThat(info.datasetName()).isEqualTo("BaseMap_P");
        assertThat(info.datasetType()).isEqualTo(DatasetType.Point);
        assertThat(info.objectCount()).isEqualTo(15);
        assertThat(info.srid()).isEqualTo(4326);
    }

    @Test
    void dataset_info_must_be_immutable_record() {
        DatasetInfo a = new DatasetInfo(1, "A", DatasetType.Point, 10, 4326);
        DatasetInfo b = new DatasetInfo(1, "A", DatasetType.Point, 10, 4326);

        // Java Record 自动生成 equals / hashCode
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void dataset_info_toString_must_include_dataset_name() {
        DatasetInfo info = new DatasetInfo(2, "BaseMap_L", DatasetType.Line, 47, 4326);

        assertThat(info.toString()).contains("BaseMap_L");
    }

    @Test
    void dataset_info_must_reject_null_name() {
        assertThatThrownBy(() -> new DatasetInfo(1, null, DatasetType.Point, 0, 4326))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void dataset_info_must_reject_null_type() {
        assertThatThrownBy(() -> new DatasetInfo(1, "Test", null, 0, 4326))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void dataset_info_object_count_can_be_zero() {
        DatasetInfo info = new DatasetInfo(1, "Empty", DatasetType.Tabular, 0, 0);

        assertThat(info.objectCount()).isEqualTo(0);
    }

    // ── FieldInfo ──────────────────────────────────────────────────────────

    @Test
    void field_info_must_expose_all_fields() {
        FieldInfo field = new FieldInfo(1, "NAME", FieldType.NText, "名称", false);

        assertThat(field.datasetId()).isEqualTo(1);
        assertThat(field.fieldName()).isEqualTo("NAME");
        assertThat(field.fieldType()).isEqualTo(FieldType.NText);
        assertThat(field.fieldAlias()).isEqualTo("名称");
        assertThat(field.required()).isFalse();
    }

    @Test
    void field_info_must_be_immutable_record() {
        FieldInfo a = new FieldInfo(1, "ID", FieldType.Int32, "编号", true);
        FieldInfo b = new FieldInfo(1, "ID", FieldType.Int32, "编号", true);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void field_info_toString_must_include_field_name() {
        FieldInfo field = new FieldInfo(1, "AREA", FieldType.Double, "面积", false);

        assertThat(field.toString()).contains("AREA");
    }

    @Test
    void field_info_must_reject_null_field_name() {
        assertThatThrownBy(() -> new FieldInfo(1, null, FieldType.Int32, "别名", false))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void field_info_must_reject_null_field_type() {
        assertThatThrownBy(() -> new FieldInfo(1, "COL", null, "别名", false))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void field_info_alias_can_be_empty_string() {
        FieldInfo field = new FieldInfo(1, "CODE", FieldType.Int32, "", false);

        assertThat(field.fieldAlias()).isEmpty();
    }
}
