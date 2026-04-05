package com.supermap.udbx.core;

import java.util.List;

/**
 * 数据集查询选项（udbx4spec 规范）。
 *
 * <p>用于 {@code list(options)} 和 {@code stream(options)} 等分页/条件查询。
 *
 * @param ids    按 SmID 过滤的 ID 列表（可选）
 * @param limit  返回最大数量（可选）
 * @param offset 起始偏移量（可选，从 0 开始）
 */
public record QueryOptions(
    List<Integer> ids,
    Integer limit,
    Integer offset
) {
    /**
     * 空查询选项（无任何过滤和分页）。
     */
    public static final QueryOptions EMPTY = new QueryOptions(null, null, null);
}
