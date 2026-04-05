package com.supermap.udbx.core;

import java.util.Objects;

/**
 * 数据集元信息（来自 SmRegister 系统表）。
 *
 * <p>对应白皮书 §2.1 表 5 SmRegister 字段定义。
 *
 * @param id          SmDatasetID —— 数据集唯一 ID
 * @param name        SmDatasetName —— 数据集名称
 * @param tableName   SmTableName —— 物理表名
 * @param kind        SmDatasetKind —— 数据集类型（udbx4spec DatasetKind）
 * @param objectCount SmObjectCount —— 要素数量
 * @param srid        SmSRID —— 坐标系 ID（0 表示无坐标系）
 * @param geometryType GAIA geoType 整数值（如 1, 5, 6, 1001 等），无几何时为 null
 */
public record DatasetInfo(
    int id,
    String name,
    String tableName,
    DatasetKind kind,
    int objectCount,
    int srid,
    Integer geometryType
) {
    public DatasetInfo {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(tableName, "tableName must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
    }

    /**
     * 便捷构造方法：tableName 默认等于 name，geometryType 默认 null。
     */
    public DatasetInfo(int id, String name, DatasetKind kind, int objectCount, int srid) {
        this(id, name, name, kind, objectCount, srid, null);
    }
}
