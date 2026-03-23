package com.supermap.udbx.dataset;

import org.locationtech.jts.geom.Point;

import java.util.Map;
import java.util.Objects;

/**
 * 点要素（Point Feature）不可变数据类。
 *
 * <p>包含系统字段 SmID、GAIA 解码后的 JTS Point 几何对象，以及用户属性字段。
 *
 * @param smId       SmID —— 要素唯一 ID（系统字段）
 * @param geometry   JTS Point 几何对象（SRID 已由 GaiaGeometryReader 设置）
 * @param attributes 用户属性字段（字段名 → 字段值），不可变视图
 */
public record PointFeature(
        int smId,
        Point geometry,
        Map<String, Object> attributes
) {
    public PointFeature {
        Objects.requireNonNull(geometry, "geometry must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
        // 防御性拷贝：过滤 null 值，确保外部无法修改
        attributes = attributes.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, Map.Entry::getValue));
    }
}
