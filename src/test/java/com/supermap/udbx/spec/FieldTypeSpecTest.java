package com.supermap.udbx.spec;

import com.supermap.udbx.core.FieldType;
import com.supermap.udbx.core.UdbxUnsupportedError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 测试：验证 FieldType 枚举值与白皮书的一致性。
 *
 * <p>对应白皮书章节：表 9（SuperMap 字段类型）
 *
 * <p>字段类型值定义：
 * <pre>
 *   1   → Boolean
 *   2   → Byte
 *   3   → Int16
 *   4   → Int32
 *   5   → Int64
 *   6   → Single（Float32）
 *   7   → Double
 *   8   → Date
 *   9   → Binary
 *   10  → Geometry
 *   11  → Char
 *   127 → NText（Unicode 变长文本，SQLite TEXT）
 *   128 → Text（ANSI 变长文本，SQLite TEXT）
 * </pre>
 */
class FieldTypeSpecTest {

    @Test
    void boolean_field_type_value_must_be_1() {
        assertThat(FieldType.BOOLEAN.getValue()).isEqualTo(1);
    }

    @Test
    void byte_field_type_value_must_be_2() {
        assertThat(FieldType.BYTE.getValue()).isEqualTo(2);
    }

    @Test
    void int16_field_type_value_must_be_3() {
        assertThat(FieldType.INT16.getValue()).isEqualTo(3);
    }

    @Test
    void int32_field_type_value_must_be_4() {
        assertThat(FieldType.INT32.getValue()).isEqualTo(4);
    }

    @Test
    void int64_field_type_value_must_be_5() {
        assertThat(FieldType.INT64.getValue()).isEqualTo(5);
    }

    @Test
    void single_field_type_value_must_be_6() {
        assertThat(FieldType.SINGLE.getValue()).isEqualTo(6);
    }

    @Test
    void double_field_type_value_must_be_7() {
        assertThat(FieldType.DOUBLE.getValue()).isEqualTo(7);
    }

    @Test
    void date_field_type_value_must_be_8() {
        assertThat(FieldType.DATE.getValue()).isEqualTo(8);
    }

    @Test
    void binary_field_type_value_must_be_9() {
        assertThat(FieldType.BINARY.getValue()).isEqualTo(9);
    }

    @Test
    void geometry_field_type_value_must_be_10() {
        assertThat(FieldType.GEOMETRY.getValue()).isEqualTo(10);
    }

    @Test
    void char_field_type_value_must_be_11() {
        assertThat(FieldType.CHAR.getValue()).isEqualTo(11);
    }

    @Test
    void ntext_field_type_value_must_be_127() {
        assertThat(FieldType.NTEXT.getValue()).isEqualTo(127);
    }

    @Test
    void text_field_type_value_must_be_128() {
        assertThat(FieldType.TEXT.getValue()).isEqualTo(128);
    }

    @Test
    void from_value_must_return_correct_enum_for_known_values() {
        assertThat(FieldType.fromValue(1)).isEqualTo(FieldType.BOOLEAN);
        assertThat(FieldType.fromValue(4)).isEqualTo(FieldType.INT32);
        assertThat(FieldType.fromValue(7)).isEqualTo(FieldType.DOUBLE);
        assertThat(FieldType.fromValue(127)).isEqualTo(FieldType.NTEXT);
        assertThat(FieldType.fromValue(128)).isEqualTo(FieldType.TEXT);
    }

    @Test
    void from_value_must_throw_for_unknown_value() {
        assertThatThrownBy(() -> FieldType.fromValue(999))
            .isInstanceOf(UdbxUnsupportedError.class)
            .hasMessageContaining("999");
    }
}
