package com.ai.mall.cart.application.cart;

import java.util.List;

/**
 * 库存可售数量出站端口（CHG-0018 DU-BE-802）。
 *
 * <p>读车装配时由 mall-cart 调 mall-inventory 内部契约
 * {@code POST /api/internal/inventory/availability}（CHG-0017 DU-BE-705 既有端点）
 * 一次批量取回精确可售数量；阈值三态（0 / 1..9 / ≥10）由 cart 读模型本地映射。
 * 本端口只提供数量查询，刻意不含 lock/release/confirm（加购不锁库存，AC-014）。
 * 实现方在依赖故障时必须抛 BusinessException(DEPENDENCY_UNAVAILABLE, 503)，
 * 由读模型降级为条目级 stockStatus=UNKNOWN，整车仍 200。
 */
public interface InventoryAvailabilityClient {

    /**
     * 按 skuId 批量取精确可售数量；返回顺序与入参顺序一致；无库存记录的 SKU 数量为 0。
     */
    List<SkuAvailability> findAvailability(List<Long> skuIds);

    /** 单 SKU 精确可售数量视图（内部口径，不对会员浏览器暴露）。 */
    record SkuAvailability(long skuId, long availableQty) {
    }
}
