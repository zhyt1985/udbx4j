package com.supermap.udbx.core;

/**
 * 数据集类型枚举（udbx4spec 规范）。
 *
 * <p>对应白皮书表 1（SmDatasetKind 字段值）。
 * Java v2.0.0 中将原 DatasetKind 重命名为 DatasetKind，常量采用 UPPER_SNAKE_CASE。
 */
public enum DatasetKind {

    TABULAR(0),
    POINT(1),
    LINE(3),
    NETWORK(4),
    REGION(5),
    TEXT(7),
    GRID(83),
    IMAGE(88),
    VOXEL_GRID(89),
    POINT_Z(101),
    LINE_Z(103),
    REGION_Z(105),
    CAD(149),
    MODEL(203),
    MODEL_TEXTURE(204),
    NETWORK_3D(205),
    MOSAIC(206);

    private final int value;

    DatasetKind(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static DatasetKind fromValue(int value) {
        for (DatasetKind kind : values()) {
            if (kind.value == value) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown DatasetKind value: " + value);
    }
}
