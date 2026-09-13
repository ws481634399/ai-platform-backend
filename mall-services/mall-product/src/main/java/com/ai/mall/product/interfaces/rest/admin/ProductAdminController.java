package com.ai.mall.product.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.product.application.product.ProductApplicationService;
import com.ai.mall.product.application.product.ProductCommands.ChangeProductStatusCommand;
import com.ai.mall.product.application.product.ProductCommands.ChangeSkuStatusCommand;
import com.ai.mall.product.application.product.ProductCommands.CreateProductCommand;
import com.ai.mall.product.application.product.ProductCommands.CreateSkuCommand;
import com.ai.mall.product.application.product.ProductCommands.ProductPageQuery;
import com.ai.mall.product.application.product.ProductCommands.UpdateProductCommand;
import com.ai.mall.product.application.product.ProductCommands.UpdateSkuCommand;
import com.ai.mall.product.domain.product.Product;
import com.ai.mall.product.domain.product.ProductAttribute;
import com.ai.mall.product.domain.product.ProductImage;
import com.ai.mall.product.domain.product.ProductRepository.ProductPageResult;
import com.ai.mall.product.domain.product.Sku;
import com.ai.mall.product.domain.product.Specification;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.AttributeRequest;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.AttributeView;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.CreateProductRequest;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.CreateSkuRequest;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.ImageRequest;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.ImageView;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.PageView;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.ProductStatusRequest;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.ProductView;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.SkuStatusRequest;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.SkuView;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.SpecificationRequest;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.SpecificationView;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.UpdateProductRequest;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductDtos.UpdateSkuRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品管理端接口：/api/admin/products。
 */
@RestController
@RequestMapping("/api/admin/products")
public class ProductAdminController {

    private final ProductApplicationService service;

    public ProductAdminController(ProductApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('product:product:list')")
    public UnifyResult<PageView<ProductView>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        ProductPageResult result = service.page(new ProductPageQuery(keyword, categoryId, brandId, status, page, size));
        List<ProductView> records = result.records().stream().map(ProductAdminController::toView).toList();
        return UnifyResult.ok(new PageView<>(records, result.total(), result.page(), result.size()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:product:detail')")
    public UnifyResult<ProductView> get(@PathVariable long id) {
        return UnifyResult.ok(toView(service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:product:create')")
    public UnifyResult<Map<String, Long>> create(@Valid @RequestBody CreateProductRequest request) {
        long id = service.create(new CreateProductCommand(request.code(), request.name(), request.subtitle(),
                request.description(), request.categoryId(), request.brandId(),
                toImageParams(request.images()), toAttributeParams(request.attributes())));
        return UnifyResult.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:product:update')")
    public UnifyResult<Void> update(@PathVariable long id, @Valid @RequestBody UpdateProductRequest request) {
        service.update(id, new UpdateProductCommand(request.name(), request.subtitle(), request.description(),
                request.categoryId(), request.brandId(),
                toImageParams(request.images()), toAttributeParams(request.attributes())));
        return UnifyResult.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('product:product:disable')")
    public UnifyResult<Void> changeStatus(@PathVariable long id,
                                          @Valid @RequestBody ProductStatusRequest request) {
        service.changeStatus(id, new ChangeProductStatusCommand(request.status()));
        return UnifyResult.ok();
    }

    @PostMapping("/{id}/skus")
    @PreAuthorize("hasAuthority('product:sku:create')")
    public UnifyResult<Map<String, Long>> addSku(@PathVariable long id,
                                                 @Valid @RequestBody CreateSkuRequest request) {
        long skuId = service.addSku(id, new CreateSkuCommand(request.skuCode(),
                toSpecificationParams(request.specifications()), request.salePriceInCents(), request.mainImageUrl()));
        return UnifyResult.ok(Map.of("id", skuId));
    }

    @PutMapping("/{id}/skus/{skuId}")
    @PreAuthorize("hasAuthority('product:sku:update')")
    public UnifyResult<Void> updateSku(@PathVariable long id, @PathVariable long skuId,
                                       @Valid @RequestBody UpdateSkuRequest request) {
        service.updateSku(id, skuId, new UpdateSkuCommand(request.salePriceInCents(), request.mainImageUrl()));
        return UnifyResult.ok();
    }

    @PutMapping("/{id}/skus/{skuId}/status")
    @PreAuthorize("hasAuthority('product:sku:disable')")
    public UnifyResult<Void> changeSkuStatus(@PathVariable long id, @PathVariable long skuId,
                                             @Valid @RequestBody SkuStatusRequest request) {
        service.changeSkuStatus(id, skuId, new ChangeSkuStatusCommand(request.status()));
        return UnifyResult.ok();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('product:product:publish')")
    public UnifyResult<Void> publish(@PathVariable long id) {
        service.publish(id);
        return UnifyResult.ok();
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAuthority('product:product:publish')")
    public UnifyResult<Void> unpublish(@PathVariable long id) {
        service.unpublish(id);
        return UnifyResult.ok();
    }

    private static List<com.ai.mall.product.application.product.ProductCommands.ImageParam> toImageParams(List<ImageRequest> images) {
        if (images == null) return null;
        return images.stream().map(i -> new com.ai.mall.product.application.product.ProductCommands.ImageParam(
                i.objectKey(), i.imageUrl(), i.imageType(), i.sortOrder(), i.mainFlag())).toList();
    }

    private static List<com.ai.mall.product.application.product.ProductCommands.AttributeParam> toAttributeParams(List<AttributeRequest> attrs) {
        if (attrs == null) return null;
        return attrs.stream().map(a -> new com.ai.mall.product.application.product.ProductCommands.AttributeParam(
                a.name(), a.value(), a.sortOrder())).toList();
    }

    private static List<com.ai.mall.product.application.product.ProductCommands.SpecificationParam> toSpecificationParams(List<SpecificationRequest> specs) {
        if (specs == null) return null;
        return specs.stream().map(s -> new com.ai.mall.product.application.product.ProductCommands.SpecificationParam(
                s.name(), s.value())).toList();
    }

    private static ProductView toView(Product product) {
        List<ImageView> images = product.getImages().stream().map(ProductAdminController::toImageView).toList();
        List<AttributeView> attributes = product.getAttributes().stream().map(ProductAdminController::toAttributeView).toList();
        List<SkuView> skus = product.getSkus().stream().map(ProductAdminController::toSkuView).toList();
        return new ProductView(product.getId(), product.getCode(), product.getName(), product.getSubtitle(),
                product.getDescription(), product.getCategoryId(), product.getBrandId(),
                product.getStatus().name(), product.getMainImageUrl(), images, attributes, skus);
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
