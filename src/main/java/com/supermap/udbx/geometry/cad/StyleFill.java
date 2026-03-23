package com.supermap.udbx.geometry.cad;

/**
 * 面填充风格（StyleFill）。
 *
 * <p>对应白皮书 §4.3.1.3。Color 字段以 int32 存储，ABGR 字节顺序。
 *
 * @param lineStyle          线符号在符号库中的 ID
 * @param lineWidth          线宽，精度 0.1mm
 * @param lineColor          线颜色（ABGR int32）
 * @param fillStyle          填充符号在符号库中的 ID
 * @param fillForecolor      填充前景色（ABGR int32）
 * @param fillBackcolor      填充背景色（ABGR int32）
 * @param fillOpaquerate     填充透明度
 * @param fillGadientType    渐变填充类型
 * @param fillAngle          填充角度，精度 0.1°
 * @param fillCenterOffsetX  填充中心点水平偏移百分比
 * @param fillCenterOffsetY  填充中心点垂直偏移百分比
 */
public record StyleFill(
        int lineStyle,
        int lineWidth,
        int lineColor,
        int fillStyle,
        int fillForecolor,
        int fillBackcolor,
        byte fillOpaquerate,
        byte fillGadientType,
        short fillAngle,
        short fillCenterOffsetX,
        short fillCenterOffsetY
) implements CadStyle {
}
