package com.ai.mall.product.application.search;

import java.util.List;

/** 搜索投影分页结果（total 与 items 同口径：ON_SALE + EXISTS 启用 SKU）。 */
public record SearchProjectionPage(
        List<SearchProjectionView> items,
        long total,
        int page,
        int size) {
}
