package com.supermap.udbx.geometry.cad;

/**
 * 线符号风格（StyleLine）。
 *
 * <p>对应白皮书 §4.3.1.2。Color 字段以 int32 存储，ABGR 字节顺序。
 *
 * @param lineStyle  线符号在符号库中的 ID
 * @param lineWidth  线宽，单位 0.1mm
 * @param lineColor  线颜色（ABGR int32）
 */
public record StyleLine(
        int lineStyle,
        int lineWidth,
        int lineColor
) implements CadStyle {
}
