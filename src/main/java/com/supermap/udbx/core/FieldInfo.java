package com.supermap.udbx.core;

import java.util.Objects;

/**
 * 字段元信息（来自 SmFieldInfo 系统表）。
 *
 * <p>对应白皮书 §2.2 表 10 SmFieldInfo 字段定义。
 *
 * @param datasetId  SmDatasetID —— 所属数据集 ID
 * @param fieldName  SmFieldName —— 字段名（与数据表列名对应）
 * @param fieldType  SmFieldType —— SuperMap 字段类型
 * @param fieldAlias SmFieldAlias —— 字段别名（可为空串）
 * @param required   SmIsRequired —— 是否必填
 */
public record FieldInfo(
    int datasetId,
    String fieldName,
    FieldType fieldType,
    String fieldAlias,
    boolean required
) {
    public FieldInfo {
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        Objects.requireNonNull(fieldType, "fieldType must not be null");
        if (fieldAlias == null) {
            fieldAlias = "";
        }
    }
}
