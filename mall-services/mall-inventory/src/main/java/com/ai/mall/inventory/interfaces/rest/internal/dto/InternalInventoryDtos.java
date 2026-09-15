package com.ai.mall.inventory.interfaces.rest.internal.dto;

import com.ai.mall.common.web.annotation.StringId;
import com.ai.mall.inventory.domain.inventory.InventoryReservation;

/**
 * 库存内部接口 DTO（供 mall-order 调用）。
 *
 * <p>CHG-0015：对外业务 ID 统一字符串输出；入参 ID 保持 long（原生兼容字符串）。
 */
public final class InternalInventoryDtos {

    private InternalInventoryDtos() {}

    public record LockRequest(String reservationId, long skuId, long quantity) {}

    public record ReleaseRequest(String reservationId) {}

    public record ConfirmRequest(String reservationId) {}

    public record ReservationView(String reservationId, @StringId long skuId, long quantity, String status) {
        public static ReservationView from(InventoryReservation reservation) {
            return new ReservationView(reservation.getReservationId(), reservation.getSkuId(),
                    reservation.getQuantity(), reservation.getStatus().name());
        }
    }

    /** 批量可售查询请求：skuIds 非空且 ≤100。 */
    public record AvailabilityRequest(java.util.List<Long> skuIds) {}

    /** 单 SKU 可售视图：精确数量（仅内部可达）。无库存记录时 availableQty=0。 */
    public record SkuAvailabilityView(@StringId long skuId, long availableQty) {}
}
