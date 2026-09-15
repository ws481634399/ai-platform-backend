package com.ai.mall.product.interfaces.rest.mall.dto;

import com.ai.mall.common.web.annotation.StringId;
import java.util.List;

/**
 * 商城首页聚合 DTO。
 */
public final class MallHomeDtos {

    private MallHomeDtos() {}

    /** 首页聚合视图。 */
    public record HomeView(
            List<CategoryEntryView> categoryEntries,
            List<ProductCardView> newArrivals,
            List<RecommendView> recommends,
            List<BannerView> banners) {}

    /** 分类入口：启用根分类，≤8。 */
    public record CategoryEntryView(@StringId long id, String name, String iconImageUrl) {}

    /** 商品卡片：与列表 item 一致的字段子集。 */
    public record ProductCardView(
            @StringId long id,
            String name,
            String mainImageUrl,
            Long minPrice,
            Long maxPrice) {}

    /** 推荐位：M3 占位 fallback 新品，source 标识来源。 */
    public record RecommendView(
            @StringId long id,
            String name,
            String mainImageUrl,
            Long minPrice,
            Long maxPrice,
            String source) {}

    /** Banner 占位（M3 空数组）。 */
    public record BannerView(@StringId long id, String imageUrl, String linkUrl) {}
}
