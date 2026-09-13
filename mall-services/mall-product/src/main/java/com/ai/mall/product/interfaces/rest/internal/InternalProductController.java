package com.ai.mall.product.interfaces.rest.internal;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.product.application.product.ProductApplicationService;
import com.ai.mall.product.domain.product.Product;
import com.ai.mall.product.domain.product.Sku;
import com.ai.mall.product.domain.product.Specification;
import com.ai.mall.product.interfaces.rest.internal.dto.ProductSnapshotView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部商品查询接口：/api/internal/products/{id}/skus/{skuId}。
 * 为 Cart/Order/Inventory/AI 提供稳定商品快照契约，不暴露 Product 领域实体。
 */
@RestController
@RequestMapping("/api/internal/products")
public class InternalProductController {

    private final ProductApplicationService service;

    public InternalProductController(ProductApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{productId}/skus/{skuId}")
    public UnifyResult<ProductSnapshotView> getSkuSnapshot(@PathVariable long productId,
                                                           @PathVariable long skuId) {
        Product product = service.getSkuSnapshot(productId, skuId);
        Sku sku = product.getSkus().stream()
                .filter(s -> s.getId() == skuId)
                .findFirst()
                .orElseThrow();
        Map<String, String> attributes = sku.getSpecifications().stream()
                .collect(Collectors.toMap(Specification::name, Specification::value,
                        (a, b) -> a, LinkedHashMap::new));
        String skuName = attributes.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(" "));
        String image = sku.getMainImageUrl() != null ? sku.getMainImageUrl() : product.getMainImageUrl();
        ProductSnapshotView snapshot = new ProductSnapshotView(
                product.getId(), sku.getId(), product.getName(), skuName,
                attributes, sku.getSalePrice().amountInCents(), image, product.getStatus().name());
        return UnifyResult.ok(snapshot);
    }
}
