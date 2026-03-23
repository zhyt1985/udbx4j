package com.supermap.udbx.geometry.cad;

/**
 * 点符号风格（StyleMarker）。
 *
 * <p>对应白皮书 §4.3.1.1。Color 字段以 int32 存储，ABGR 字节顺序。
 *
 * @param markerStyle     点符号在符号库中的 ID
 * @param markerSize      符号大小，精度 0.1mm
 * @param markerAngle     符号旋转角度，单位 0.1 度
 * @param markerColor     符号颜色（ABGR int32）
 * @param markerWidth     符号宽度
 * @param markerHeight    符号高度
 * @param fillOpaqueRate  填充透明度
 * @param fillGradientType 渐变填充类型
 * @param fillAngle       填充角度
 * @param fillCenterOffsetX 填充中心点水平偏移百分比
 * @param fillCenterOffsetY 填充中心点垂直偏移百分比
 * @param fillBackcolor   填充背景色（ABGR int32）
 */
public record StyleMarker(
        int markerStyle,
        int markerSize,
        int markerAngle,
        int markerColor,
        int markerWidth,
        int markerHeight,
        byte fillOpaqueRate,
        byte fillGradientType,
        short fillAngle,
        short fillCenterOffsetX,
        short fillCenterOffsetY,
        int fillBackcolor
) implements CadStyle {
}
