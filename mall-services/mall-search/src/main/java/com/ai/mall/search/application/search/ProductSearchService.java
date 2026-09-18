package com.ai.mall.search.application.search;

import com.ai.mall.common.config.FeatureGate;
import com.ai.mall.search.domain.search.ProductSearchItem;
import com.ai.mall.search.domain.search.ProductSearchPort;
import com.ai.mall.search.domain.search.SearchPage;
import com.ai.mall.search.domain.search.SearchQuery;
import com.ai.mall.search.domain.search.SortMode;
import org.springframework.stereotype.Service;

/**
 * 商品搜索应用服务（CHG-0020 DU-BE-502/511）。
 *
 * <p>参数归一（非法值安全回退/拒绝）后交出站端口：
 * <ul>
 *   <li>page &lt; 1 → 1；size 缺省 20、上限 100；keyword trim 后 ≤64</li>
 *   <li>价格非负、min≤max；categoryId/brandId 非正忽略（不参与过滤）</li>
 *   <li>sort 未知值静默回退 DEFAULT</li>
 * </ul>
 */
@Service
public class ProductSearchService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final int MAX_KEYWORD_LENGTH = 64;

    private final ProductSearchPort productSearchPort;
    private final FeatureGate featureGate;

    /** CHG-0022：search.enabled 关闭时显式 403（缺键/故障默认放行，避免基础设施故障回归）。 */
    private static final String FEATURE_KEY = "search.enabled";

    public ProductSearchService(ProductSearchPort productSearchPort, FeatureGate featureGate) {
        this.productSearchPort = productSearchPort;
        this.featureGate = featureGate;
    }

    public SearchPage<ProductSearchItem> search(String rawKeyword, Long categoryId, Long brandId,
                                               Long minPriceFen, Long maxPriceFen,
                                               String sort, Integer page, Integer size) {
        featureGate.ensureEnabled(FEATURE_KEY);
        String keyword = normalizeKeyword(rawKeyword);
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("keyword 长度不能超过 " + MAX_KEYWORD_LENGTH);
        }
        if (minPriceFen != null && minPriceFen < 0) {
            throw new IllegalArgumentException("minPriceFen 不能为负");
        }
        if (maxPriceFen != null && maxPriceFen < 0) {
            throw new IllegalArgumentException("maxPriceFen 不能为负");
        }
        if (minPriceFen != null && maxPriceFen != null && minPriceFen > maxPriceFen) {
            throw new IllegalArgumentException("minPriceFen 不能大于 maxPriceFen");
        }
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedSize;
        if (size == null || size < 1) {
            normalizedSize = DEFAULT_SIZE;
        } else {
            normalizedSize = Math.min(size, MAX_SIZE);
        }
        SortMode mode = SortMode.from(sort);
        SearchQuery query = SearchQuery.normalized(keyword,
                positiveOrNull(categoryId), positiveOrNull(brandId),
                minPriceFen, maxPriceFen, mode, normalizedPage, normalizedSize);
        return productSearchPort.search(query);
    }

    private static String normalizeKeyword(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Long positiveOrNull(Long id) {
        return id != null && id > 0 ? id : null;
    }
}
