package com.ai.mall.product.application.home;

import com.ai.mall.product.application.category.CategoryApplicationService;
import com.ai.mall.product.application.product.ProductApplicationService;
import com.ai.mall.product.application.product.ProductCommands.ProductPageQuery;
import com.ai.mall.product.domain.category.Category;
import com.ai.mall.product.domain.category.CategoryLevel;
import com.ai.mall.product.domain.product.Product;
import com.ai.mall.product.domain.product.ProductRepository.PriceRange;
import com.ai.mall.product.domain.product.ProductRepository.ProductPageResult;
import com.ai.mall.product.interfaces.rest.mall.dto.MallHomeDtos.BannerView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallHomeDtos.CategoryEntryView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallHomeDtos.HomeView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallHomeDtos.ProductCardView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallHomeDtos.RecommendView;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商城首页聚合应用服务：分类入口 + 新品 + 推荐（fallback）+ banners（空占位）。
 *
 * <p>三路只读查询顺序执行（M3 数据量小）；单路失败按部分降级，整体数据访问异常由全局 503 处理。
 */
@Service
public class HomeApplicationService {

    /** 首页分类入口最大数量。 */
    private static final int MAX_CATEGORY_ENTRIES = 8;
    /** 首页新品/推荐数量。 */
    private static final int HOME_PRODUCT_LIMIT = 10;
    /** 推荐来源占位标识。 */
    private static final String RECOMMEND_SOURCE_FALLBACK = "FALLBACK_NEWEST";

    private final CategoryApplicationService categoryService;
    private final ProductApplicationService productService;

    public HomeApplicationService(CategoryApplicationService categoryService,
                                  ProductApplicationService productService) {
        this.categoryService = categoryService;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public HomeView home() {
        List<CategoryEntryView> categoryEntries = loadCategoryEntries();
        List<ProductCardView> newArrivals = loadNewArrivals();
        List<RecommendView> recommends = newArrivals.stream()
                .map(card -> new RecommendView(card.id(), card.name(), card.mainImageUrl(),
                        card.minPrice(), card.maxPrice(), RECOMMEND_SOURCE_FALLBACK))
                .toList();
        return new HomeView(categoryEntries, newArrivals, recommends, List.of());
    }

    private List<CategoryEntryView> loadCategoryEntries() {
        return categoryService.mallTree().stream()
                .filter(c -> c.getParentId() == CategoryLevel.ROOT_PARENT_ID && c.isEnabled())
                .sorted(Comparator.comparingInt(Category::getSort).thenComparingLong(Category::getId))
                .limit(MAX_CATEGORY_ENTRIES)
                .map(c -> new CategoryEntryView(c.getId(), c.getName(), null))
                .toList();
    }

    private List<ProductCardView> loadNewArrivals() {
        ProductPageResult result = productService.mallPage(
                new ProductPageQuery(null, null, null, null, 1, HOME_PRODUCT_LIMIT));
        return result.records().stream()
                .map(product -> toCardView(product, result.priceRanges().get(product.getId())))
                .toList();
    }

    private static ProductCardView toCardView(Product product, PriceRange priceRange) {
        Long minPrice = priceRange == null ? null : priceRange.minPrice();
        Long maxPrice = priceRange == null ? null : priceRange.maxPrice();
        return new ProductCardView(product.getId(), product.getName(),
                product.getMainImageUrl(), minPrice, maxPrice);
    }
}
