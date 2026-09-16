package com.ai.mall.product.interfaces.rest.internal;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.product.application.product.ProductApplicationService;
import com.ai.mall.product.application.sku.SkuBatchApplicationService;
import com.ai.mall.product.application.sku.SkuBatchApplicationService.SkuBatchItem;
import com.ai.mall.product.domain.product.Product;
import com.ai.mall.product.domain.product.Sku;
import com.ai.mall.product.domain.product.Specification;
import com.ai.mall.product.interfaces.rest.internal.dto.ProductSnapshotView;
import com.ai.mall.product.interfaces.rest.internal.dto.SkuBatchDtos.SkuBatchItemView;
import com.ai.mall.product.interfaces.rest.internal.dto.SkuBatchDtos.SkuBatchRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final SkuBatchApplicationService skuBatchService;

    public InternalProductController(ProductApplicationService service, SkuBatchApplicationService skuBatchService) {
        this.service = service;
        this.skuBatchService = skuBatchService;
    }

    @GetMapping("/{productId}/skus/{skuId}")
    public UnifyResult<ProductSnapshotView> getSkuSnapshot(@PathVariable("productId") long productId,
                                                           @PathVariable("skuId") long skuId) {
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

    @GetMapping("/skus/{skuId}")
    public UnifyResult<Boolean> existsSku(@PathVariable("skuId") long skuId) {
        return UnifyResult.ok(service.existsSku(skuId));
    }

    /**
     * CHG-0018 DU-BE-801：SKU 批量可售快照（cart 加购前一次调用）。
     * 返 product ON_SALE + sku ENABLED 双状态、价格（整数分）、图、规格；
     * 命中不到的 skuId 占位 salable=false；本接口不触发任何库存查询。
     */
    @PostMapping("/skus/batch")
    public UnifyResult<List<SkuBatchItemView>> skuBatch(@Valid @RequestBody SkuBatchRequest request) {
        List<SkuBatchItem> items = skuBatchService.batch(request.skuIds());
        List<SkuBatchItemView> views = items.stream()
                .map(item -> new SkuBatchItemView(item.productId(), item.productName(), item.productStatus(),
                        item.skuId(), item.skuCode(), item.skuStatus(), item.salePriceInCents(),
                        item.mainImageUrl(), item.specifications(), item.salable()))
                .toList();
        return UnifyResult.ok(views);
    }
}
