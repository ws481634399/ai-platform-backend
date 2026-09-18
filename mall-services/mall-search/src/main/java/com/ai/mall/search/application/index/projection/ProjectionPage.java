package com.ai.mall.search.application.index.projection;

import java.util.List;

/** 投影分页结果（字段口径与搜索分页一致：items/total/page/size）。 */
public record ProjectionPage(
        List<ProductProjectionView> items,
        long total,
        int page,
        int size) {
}
