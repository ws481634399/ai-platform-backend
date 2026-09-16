package com.ai.mall.product.application.sku;

import com.ai.mall.product.domain.product.Product;
import com.ai.mall.product.domain.product.ProductRepository;
import com.ai.mall.product.domain.product.ProductStatus;
import com.ai.mall.product.domain.product.Sku;
import com.ai.mall.product.domain.product.SkuStatus;
import com.ai.mall.product.domain.product.Specification;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SKU 批量快照应用服务（CHG-0018 DU-BE-801）。
 *
 * <p>为 mall-cart 加购可售校验提供一次调用的 product+sku 双状态快照：
 * 仅做读模型装配，不查库存、不锁库存。命中不到的 skuId 同样占位返回
 * （{@code salable=false}），由调用方决定拒绝策略，避免存在性枚举差异。
 */
@Service
public class SkuBatchApplicationService {

    /** 加购校验单次批量上限（与购物车条目上限一致）。 */
    public static final int MAX_BATCH = 100;

    private final ProductRepository productRepository;

    public SkuBatchApplicationService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 按请求顺序返回每个 skuId 的快照项；重复 id 去重但保持首次出现顺序。
     */
    @Transactional(readOnly = true)
    public List<SkuBatchItem> batch(List<Long> skuIds) {
        List<Long> normalized = skuIds == null ? List.of()
                : skuIds.stream().filter(id -> id != null && id > 0).distinct().limit(MAX_BATCH).toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<Product> products = productRepository.findBySkuIds(normalized);
        // skuId -> (product, sku)
        Map<Long, Product> productBySku = new LinkedHashMap<>();
        Map<Long, Sku> skuBySku = new LinkedHashMap<>();
        for (Product product : products) {
            for (Sku sku : product.getSkus()) {
                productBySku.putIfAbsent(sku.getId(), product);
                skuBySku.putIfAbsent(sku.getId(), sku);
            }
        }
        return normalized.stream()
                .map(id -> toItem(id, productBySku.get(id), skuBySku.get(id)))
                .toList();
    }

    private static SkuBatchItem toItem(long skuId, Product product, Sku sku) {
        if (product == null || sku == null) {
            // 不存在或已软删：不暴露存在性，统一不可售占位
            return SkuBatchItem.missing(skuId);
        }
        boolean salable = product.getStatus() == ProductStatus.ON_SALE && sku.getStatus() == SkuStatus.ENABLED;
        Map<String, String> specs = new LinkedHashMap<>();
        for (Specification spec : sku.getSpecifications()) {
            specs.put(spec.name(), spec.value());
        }
        String image = sku.getMainImageUrl() != null ? sku.getMainImageUrl() : product.getMainImageUrl();
        return new SkuBatchItem(product.getId(), product.getName(), product.getStatus().name(),
                sku.getId(), sku.getCode(), sku.getStatus().name(),
                sku.getSalePrice().amountInCents(), image, specs, salable);
    }

    /**
     * SKU 可售快照项。product 或 sku 任一不存在时字段为 null、salable=false。
     */
    public record SkuBatchItem(Long productId, String productName, String productStatus,
                               Long skuId, String skuCode, String skuStatus,
                               Long salePriceInCents, String mainImageUrl,
                               Map<String, String> specifications, boolean salable) {

        public static SkuBatchItem missing(long skuId) {
            return new SkuBatchItem(null, null, null, skuId, null, null,
                    null, null, Map.of(), false);
        }
    }
}
