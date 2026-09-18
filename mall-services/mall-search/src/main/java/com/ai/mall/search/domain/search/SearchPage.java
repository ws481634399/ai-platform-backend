package com.ai.mall.search.domain.search;

import java.util.List;

/**
 * 搜索分页结果（CHG-0020）：items + total + 当前分页切片。
 */
public record SearchPage<T>(
        List<T> items,
        long total,
        int page,
        int size) {
}
