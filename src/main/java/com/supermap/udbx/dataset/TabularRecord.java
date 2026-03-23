package com.supermap.udbx.dataset;

import java.util.Map;
import java.util.Objects;

/**
 * 纯属性表记录（不可变 Record）。
 *
 * <p>包含 SmID 和用户属性字段，无几何体。
 * 对应白皮书 §3.1.1（Tabular 数据集记录结构）。
 *
 * @param smId       SmID —— 记录唯一 ID（系统字段）
 * @param attributes 用户属性字段（字段名 → 字段值），不可变视图
 */
public record TabularRecord(
        int smId,
        Map<String, Object> attributes
) {
    public TabularRecord {
        Objects.requireNonNull(attributes, "attributes must not be null");
        // 防御性拷贝：过滤 null 值
        attributes = attributes.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, Map.Entry::getValue));
    }
}
