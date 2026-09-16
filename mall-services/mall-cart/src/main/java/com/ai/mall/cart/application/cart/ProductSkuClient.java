package com.ai.mall.cart.application.cart;

import java.util.List;

/**
 * 商品 SKU 快照出站端口（CHG-0018 DU-BE-801）。
 *
 * <p>加购前由 mall-cart 调 mall-product 内部契约
 * {@code POST /api/internal/products/skus/batch} 一次取齐可售状态与价格；
 * 本端口刻意不包含任何库存语义（不查/不锁库存，AC-014）。
 * 实现方在依赖故障时必须抛 BusinessException(DEPENDENCY_UNAVAILABLE, 503)，
 * 与"商品业务不可售"明确区分。
 */
public interface ProductSkuClient {

    /**
     * 按 skuId 批量取快照；返回顺序与入参去重后顺序一致；
     * 命中不到的 skuId 以 {@link SkuSnapshot#salable()}=false 占位。
     */
    List<SkuSnapshot> findSnapshots(List<Long> skuIds);

    /**
     * 商品/SKU 可售快照。product/sku 任一不存在时 id/价格字段为 null。
     *
     * @param productId         商品 ID（雪花 ID）
     * @param productName       商品名称
     * @param productStatus     商品状态名（ON_SALE 等）
     * @param skuId             SKU ID
     * @param skuCode           SKU 编码
     * @param skuStatus         SKU 状态名（ENABLED 等）
     * @param salePriceInCents  当前售价（整数分）
     * @param mainImageUrl      SKU 主图（缺省回退商品主图）
     * @param specifications    规格名值对
     * @param salable           product=ON_SALE 且 sku=ENABLED 才为 true
     */
    record SkuSnapshot(Long productId, String productName, String productStatus,
                       Long skuId, String skuCode, String skuStatus,
                       Long salePriceInCents, String mainImageUrl,
                       java.util.Map<String, String> specifications, boolean salable) {
    }
}
