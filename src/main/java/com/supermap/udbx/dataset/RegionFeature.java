package com.supermap.udbx.dataset;

import org.locationtech.jts.geom.MultiPolygon;

import java.util.Map;
import java.util.Objects;

/**
 * 面数据集要素（不可变 Record）。
 *
 * <p>包含 SmID、MultiPolygon 几何体和用户属性字段。
 * 对应白皮书 §3.1.4（面数据集记录结构）。
 *
 * @param smId       SmID —— 要素唯一 ID（系统字段）
 * @param geometry   JTS MultiPolygon 几何对象（SRID 已由 GaiaGeometryReader 设置）
 * @param attributes 用户属性字段（字段名 → 字段值），不可变视图
 */
public record RegionFeature(
        int smId,
        MultiPolygon geometry,
        Map<String, Object> attributes
) {
    public RegionFeature {
        Objects.requireNonNull(geometry, "geometry must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
        // 防御性拷贝：过滤 null 值
        attributes = attributes.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, Map.Entry::getValue));
    }
}
