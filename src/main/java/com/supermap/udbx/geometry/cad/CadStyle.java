package com.supermap.udbx.geometry.cad;

/**
 * CAD 风格（Style）的密封接口。
 *
 * <p>按对象维度分为：
 * <ul>
 *   <li>{@link StyleMarker} — 点符号（用于 GeoPoint）</li>
 *   <li>{@link StyleLine}   — 线符号（用于 GeoLine、曲线等）</li>
 *   <li>{@link StyleFill}  — 面填充风格（用于 GeoRegion、GeoRect 等）</li>
 * </ul>
 *
 * <p>对应白皮书 §4.3.1。
 */
public sealed interface CadStyle permits StyleMarker, StyleLine, StyleFill {
}
