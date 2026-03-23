package com.supermap.udbx.dataset;

import com.supermap.udbx.core.DatasetInfo;

import java.sql.Connection;

/**
 * 矢量数据集基类。
 *
 * <p>矢量数据集（Point、Line、Region、CAD 等）的公共基类。
 * 数据表名称约定与数据集名称相同（如 BaseMap_P）。
 */
public abstract class VectorDataset extends Dataset {

    protected VectorDataset(Connection conn, DatasetInfo info) {
        super(conn, info);
    }

    /**
     * 返回该数据集对应的数据表名称。
     *
     * <p>UDBX 约定：矢量数据集的数据表名称与 SmDatasetName 相同。
     */
    protected String getTableName() {
        return info.datasetName();
    }
}
