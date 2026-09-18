package com.ai.mall.search.interfaces.rest.mall;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.search.application.search.ProductSearchService;
import com.ai.mall.search.domain.search.ProductSearchItem;
import com.ai.mall.search.domain.search.SearchPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城商品搜索端点（CHG-0020 PUBLIC，本地无安全约束，鉴权在网关收口）。
 */
@RestController
@RequestMapping("/api/mall/search")
public class MallSearchController {

    private final ProductSearchService productSearchService;

    public MallSearchController(ProductSearchService productSearchService) {
        this.productSearchService = productSearchService;
    }

    @GetMapping("/products")
    public UnifyResult<SearchPage<ProductSearchItem>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long minPriceFen,
            @RequestParam(required = false) Long maxPriceFen,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return UnifyResult.ok(productSearchService.search(keyword, categoryId, brandId,
                minPriceFen, maxPriceFen, sort, page, size));
    }
}
