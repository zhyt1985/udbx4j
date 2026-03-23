package com.supermap.udbx.core;

/**
 * 数据集类型枚举。
 *
 * <p>对应白皮书表 1（SmDatasetType 字段值）。
 */
public enum DatasetType {

    Tabular(0),
    Point(1),
    Line(3),
    Network(4),
    Region(5),
    Text(7),
    Grid(83),
    Image(88),
    VoxelGrid(89),
    PointZ(101),
    LineZ(103),
    RegionZ(105),
    CAD(149),
    Model(203),
    ModelTexture(204),
    Network3D(205),
    Mosaic(206);

    private final int value;

    DatasetType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static DatasetType fromValue(int value) {
        for (DatasetType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown DatasetType value: " + value);
    }
}
