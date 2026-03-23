package com.supermap.udbx.core;

import java.util.Objects;

/**
 * 数据集元信息（来自 SmRegister 系统表）。
 *
 * <p>对应白皮书 §2.1 表 5 SmRegister 字段定义。
 *
 * @param datasetId   SmDatasetID —— 数据集唯一 ID
 * @param datasetName SmDatasetName —— 数据集名称
 * @param datasetType SmDatasetType —— 数据集类型
 * @param objectCount SmObjectCount —— 要素数量
 * @param srid        SmSRID —— 坐标系 ID（0 表示无坐标系）
 */
public record DatasetInfo(
    int datasetId,
    String datasetName,
    DatasetType datasetType,
    int objectCount,
    int srid
) {
    public DatasetInfo {
        Objects.requireNonNull(datasetName, "datasetName must not be null");
        Objects.requireNonNull(datasetType, "datasetType must not be null");
    }
}
