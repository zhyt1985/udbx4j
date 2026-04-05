package com.supermap.udbx.core;

/**
 * SuperMap 字段类型枚举。
 *
 * <p>对应白皮书表 9（SmFieldInfo.SmFieldType 字段值）。
 */
public enum FieldType {

    BOOLEAN(1),
    BYTE(2),
    INT16(3),
    INT32(4),
    INT64(5),
    SINGLE(6),
    DOUBLE(7),
    DATE(8),
    BINARY(9),
    GEOMETRY(10),
    CHAR(11),
    NTEXT(127),
    TEXT(128),
    TIME(16);  // TODO: 确认白皮书中的确切名称

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
        throw new UdbxUnsupportedError("Unknown FieldType value: " + value);
    }
}
