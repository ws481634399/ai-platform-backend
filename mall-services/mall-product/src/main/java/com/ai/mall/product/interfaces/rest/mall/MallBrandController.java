package com.ai.mall.product.interfaces.rest.mall;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.product.application.brand.BrandApplicationService;
import com.ai.mall.product.domain.brand.Brand;
import com.ai.mall.product.domain.brand.BrandRepository.BrandPageResult;
import com.ai.mall.product.interfaces.rest.mall.dto.MallCatalogDtos.BrandPageView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallCatalogDtos.BrandView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城公开品牌接口：/api/mall/brands（匿名，仅 ENABLED，keyword 模糊，size≤200）。
 */
@RestController
@RequestMapping("/api/mall/brands")
public class MallBrandController {

    private final BrandApplicationService service;

    public MallBrandController(BrandApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public UnifyResult<BrandPageView> page(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        BrandPageResult result = service.mallPage(keyword, page, size);
        List<BrandView> items = result.records().stream().map(MallBrandController::toView).toList();
        return UnifyResult.ok(new BrandPageView(items, result.total(), result.page(), result.size()));
    }

    private static BrandView toView(Brand brand) {
        return new BrandView(brand.getId(), brand.getName(), brand.getLogo(), brand.getSort());
    }
}
