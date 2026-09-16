package com.ai.mall.product.interfaces.rest.internal.dto;

import com.ai.mall.common.web.annotation.StringId;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 内部 SKU 批量快照契约（CHG-0018 DU-BE-801）：
 * POST /api/internal/products/skus/batch。
 */
public final class SkuBatchDtos {

    private SkuBatchDtos() {
    }

    /** 批量快照请求：最多 100 个 SKU。 */
    public record SkuBatchRequest(
            @NotEmpty(message = "skuIds 不能为空")
            @Size(max = 100, message = "单次最多查询100个SKU")
            List<@NotNull @Positive Long> skuIds) {
    }

    /**
     * 单个 SKU 快照。不存在/已删除的 skuId 仅回 skuId 且 salable=false。
     * ID 序列化为字符串（雪花 ID JS 精度保护）；金额为整数分 number。
     */
    public record SkuBatchItemView(
            @StringId Long productId,
            String productName,
            String productStatus,
            @StringId Long skuId,
            String skuCode,
            String skuStatus,
            Long salePriceInCents,
            String mainImageUrl,
            Map<String, String> specifications,
            boolean salable) {
    }
}
