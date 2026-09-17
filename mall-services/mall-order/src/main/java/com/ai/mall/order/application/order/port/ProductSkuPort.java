package com.ai.mall.order.application.order.port;

import java.util.List;
import java.util.Map;

/**
 * mall-product 出站端口：SKU 批量可售快照（CHG-0019）。
 *
 * <p>预览与正式下单都必须实时重查商品双状态/最新价；前端任何价格字段不入领域。
 * 传输/信封故障由实现归一为 503，业务不可售（salable=false）正常返回由调用方判定。
 */
public interface ProductSkuPort {

    List<SkuSnapshot> batchSnapshots(List<Long> skuIds);

    /** 商品/SKU 快照（ID 为内部 Long；找不到的 skuId 以 salable=false 占位返回）。 */
    record SkuSnapshot(Long productId, String productName, String productStatus, Long skuId, String skuCode,
                       String skuStatus, Long salePriceInCents, String mainImageUrl,
                       Map<String, String> specifications, boolean salable) {
    }
}
