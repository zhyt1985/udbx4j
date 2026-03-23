package com.supermap.udbx.dataset;

import com.supermap.udbx.geometry.cad.CadGeometry;

import java.util.Map;
import java.util.Objects;

/**
 * CAD 数据集要素（不可变 Record）。
 *
 * <p>包含 SmID、CAD 几何体（含风格信息）和用户属性字段。
 * 对应白皮书 §4.3（CAD 数据集记录结构）。
 *
 * @param smId       SmID —— 要素唯一 ID（系统字段）
 * @param geometry   CAD 几何对象（含 StyleMarker/StyleLine/StyleFill 风格信息）
 * @param attributes 用户属性字段（字段名 → 字段值），不可变视图
 */
public record CadFeature(
        int smId,
        CadGeometry geometry,
        Map<String, Object> attributes
) {
    public CadFeature {
        Objects.requireNonNull(geometry, "geometry must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
        attributes = attributes.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, Map.Entry::getValue));
    }
}
