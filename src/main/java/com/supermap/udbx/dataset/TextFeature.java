package com.supermap.udbx.dataset;

import java.util.Map;
import java.util.Objects;

/**
 * 文本数据集要素（不可变 Record）。
 *
 * <p>包含 id（对应 SmID）、文本内容和用户属性字段。
 * 对应白皮书 §3.1.5（文本数据集记录结构）和 §4.4（文本对象存储结构）。
 *
 * <p>文本几何数据（GeoText）包含：
 * <ul>
 *   <li>文本内容</li>
 *   <li>锚点位置（X, Y）</li>
 *   <li>文本风格（字体、大小、颜色等）</li>
 *   <li>旋转角度</li>
 * </ul>
 *
 * @param id         要素唯一 ID（对应 SmID）
 * @param text       文本内容
 * @param x          锚点 X 坐标
 * @param y          锚点 Y 坐标
 * @param attributes 用户属性字段（字段名 → 字段值），不可变视图
 */
public record TextFeature(
        int id,
        String text,
        double x,
        double y,
        Map<String, Object> attributes
) {
    public TextFeature {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
        attributes = attributes.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 创建不带属性的文本要素。
     */
    public TextFeature(int id, String text, double x, double y) {
        this(id, text, x, y, Map.of());
    }
}
