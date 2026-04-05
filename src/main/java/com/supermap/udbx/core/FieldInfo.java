package com.supermap.udbx.core;

import java.util.Objects;

/**
 * 字段元信息（来自 SmFieldInfo 系统表）。
 *
 * <p>对应白皮书 §2.2 表 10 SmFieldInfo 字段定义。
 * 遵循 udbx4spec FieldInfo 规范。
 *
 * @param datasetId    SmDatasetID —— 所属数据集 ID
 * @param name         SmFieldName —— 字段名（与数据表列名对应）
 * @param fieldType    SmFieldType —— SuperMap 字段类型
 * @param alias        SmFieldAlias —— 字段别名（可为空串）
 * @param required     SmIsRequired —— 是否必填
 * @param nullable     是否可为 null（建议在 DDL 中使用）
 * @param defaultValue 默认值
 */
public record FieldInfo(
    int datasetId,
    String name,
    FieldType fieldType,
    String alias,
    boolean required,
    Boolean nullable,
    Object defaultValue
) {
    public FieldInfo {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(fieldType, "fieldType must not be null");
        if (alias == null) {
            alias = "";
        }
    }

    /**
     * 兼容旧构造方式（nullable 和 defaultValue 使用默认值）。
     */
    public FieldInfo(int datasetId, String name, FieldType fieldType, String alias, boolean required) {
        this(datasetId, name, fieldType, alias, required, null, null);
    }
}
