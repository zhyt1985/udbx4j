package com.supermap.udbx.core;

/**
 * SuperMap 字段类型枚举。
 *
 * <p>对应白皮书表 9（SmFieldInfo.SmFieldType 字段值）。
 */
public enum FieldType {

    Boolean(1),
    Byte(2),
    Int16(3),
    Int32(4),
    Int64(5),
    Single(6),
    Double(7),
    Date(8),
    Binary(9),
    Geometry(10),
    Char(11),
    NText(127),
    Text(128),
    Time(16);  // TODO: 确认白皮书中的确切名称

    private final int value;

    FieldType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static FieldType fromValue(int value) {
        for (FieldType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown FieldType value: " + value);
    }
}
