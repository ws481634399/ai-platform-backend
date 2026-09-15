package com.ai.mall.product.interfaces.rest.mall.dto;

import com.ai.mall.common.web.annotation.StringId;
import java.util.List;

/**
 * SKU 可售状态公开查询 DTO。
 *
 * <p>白名单：响应仅含 skuId + stockStatus，禁止精确数量字段（物理隔离）。
 */
public final class MallSkuAvailabilityDtos {

    private MallSkuAvailabilityDtos() {}

    /** 批量可售查询请求：skuIds 非空且 ≤100。 */
    public record SkuAvailabilityRequest(List<Long> skuIds) {}

    /** 单 SKU 可售状态视图：仅 skuId + stockStatus（无数量）。 */
    public record SkuAvailabilityView(@StringId long skuId, StockStatus stockStatus) {}
}
