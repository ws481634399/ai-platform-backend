package com.ai.mall.search.domain.search;

/**
 * 商品搜索查询值对象（CHG-0020）。
 *
 * <p>价格单位为整数分；categoryId/brandId 为平台主键。
 * 归一化由应用层完成，本对象仅承载归一后结果。
 */
public record SearchQuery(
        String keyword,
        Long categoryId,
        Long brandId,
        Long minPriceFen,
        Long maxPriceFen,
        SortMode sort,
        int page,
        int size) {

    public static SearchQuery normalized(String keyword, Long categoryId, Long brandId,
                                        Long minPriceFen, Long maxPriceFen,
                                        SortMode sort, int page, int size) {
        return new SearchQuery(keyword, categoryId, brandId, minPriceFen, maxPriceFen,
                sort, page, size);
    }
}
