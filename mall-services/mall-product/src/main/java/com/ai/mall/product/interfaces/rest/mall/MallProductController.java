package com.ai.mall.product.interfaces.rest.mall;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.product.application.product.ProductApplicationService;
import com.ai.mall.product.application.product.ProductCommands.ProductPageQuery;
import com.ai.mall.product.domain.product.Product;
import com.ai.mall.product.domain.product.ProductAttribute;
import com.ai.mall.product.domain.product.ProductImage;
import com.ai.mall.product.domain.product.ProductRepository.PriceRange;
import com.ai.mall.product.domain.product.ProductRepository.ProductPageResult;
import com.ai.mall.product.domain.product.Sku;
import com.ai.mall.product.domain.product.Specification;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.AttributeView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.ImageView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.MallProductDetailView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.MallProductListItemView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.PageView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.SkuView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.SpecificationView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城商品查询接口：/api/mall/products（公开，仅返回 ON_SALE 商品）。
 */
@RestController
@RequestMapping("/api/mall/products")
public class MallProductController {

    private final ProductApplicationService service;

    public MallProductController(ProductApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public UnifyResult<PageView<MallProductListItemView>> page(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "brandId", required = false) Long brandId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        ProductPageResult result = service.mallPage(new ProductPageQuery(keyword, categoryId, brandId, null, page, size));
        List<MallProductListItemView> records = result.records().stream()
                .map(product -> toListItemView(product, result.priceRanges().get(product.getId()))).toList();
        return UnifyResult.ok(new PageView<>(records, result.total(), result.page(), result.size()));
    }

    @GetMapping("/{id}")
    public UnifyResult<MallProductDetailView> get(@PathVariable("id") long id) {
        return UnifyResult.ok(toDetailView(service.getMallById(id)));
    }

    private static MallProductListItemView toListItemView(Product product, PriceRange priceRange) {
        // EXISTS 已保证每个列表商品至少有一个启用 SKU，priceRange 必存在；null 兜底仅作防御
        Long minPrice = priceRange == null ? null : priceRange.minPrice();
        Long maxPrice = priceRange == null ? null : priceRange.maxPrice();
        return new MallProductListItemView(product.getId(), product.getCode(), product.getName(),
                product.getSubtitle(), product.getCategoryId(), product.getBrandId(),
                product.getMainImageUrl(), minPrice, maxPrice, product.getStatus().name());
    }

    private static MallProductDetailView toDetailView(Product product) {
        List<ImageView> images = product.getImages().stream().map(MallProductController::toImageView).toList();
        List<AttributeView> attributes = product.getAttributes().stream()
                .map(MallProductController::toAttributeView).toList();
        List<SkuView> skus = product.getSkus().stream().map(MallProductController::toSkuView).toList();
        return new MallProductDetailView(product.getId(), product.getCode(), product.getName(),
                product.getSubtitle(), product.getDescription(), product.getCategoryId(), product.getBrandId(),
                product.getMainImageUrl(), images, attributes, skus, product.getStatus().name());
    }

    private static SkuView toSkuView(Sku sku) {
        List<SpecificationView> specs = sku.getSpecifications().stream()
                .map(s -> new SpecificationView(s.name(), s.value())).toList();
        return new SkuView(sku.getId(), sku.getCode(), specs,
                sku.getSalePrice().amountInCents(), sku.getStatus().name(), sku.getMainImageUrl());
    }

    private static ImageView toImageView(ProductImage image) {
        return new ImageView(image.id(), image.objectKey(), image.imageUrl(),
                image.imageType().name(), image.sortOrder(), image.mainFlag());
    }

    private static AttributeView toAttributeView(ProductAttribute attr) {
        return new AttributeView(attr.id(), attr.name(), attr.value(), attr.sortOrder());
    }
}
