package com.ai.mall.product.interfaces.rest.mall.dto;

import com.ai.mall.common.web.annotation.StringId;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 公开 SKU 快照查询 DTO（CHG-0018 DU-BE-803）。
 *
 * <p>游客购物车展示用：仅返回 product ON_SALE 且 sku ENABLED 的条目，
 * 不可售/不存在的 skuId 不返回（前端据缺失键标失效）。
 */
public final class MallSkuItemsDtos {

    private MallSkuItemsDtos() {
    }

    /** 批量快照请求：skuIds 非空且 ≤100。 */
    public record SkuItemsRequest(
            @NotEmpty(message = "skuIds 不能为空")
            @Size(max = 100, message = "单次最多查询100个SKU")
            List<Long> skuIds) {
    }

    /** 单 SKU 公开快照：仅可售条目返回；ID 字符串化；金额整数分。 */
    public record SkuItemView(
            @StringId long productId,
            String productName,
            @StringId long skuId,
            String skuCode,
            Long salePriceInCents,
            String mainImageUrl,
            Map<String, String> specifications) {
    }
}
