package com.ai.mall.inventory.interfaces.rest.internal.dto;

import com.ai.mall.inventory.domain.inventory.InventoryReservation;

/**
 * 库存内部接口 DTO（供 mall-order 调用）。
 */
public final class InternalInventoryDtos {

    private InternalInventoryDtos() {}

    public record LockRequest(String reservationId, long skuId, long quantity) {}

    public record ReleaseRequest(String reservationId) {}

    public record ConfirmRequest(String reservationId) {}

    public record ReservationView(String reservationId, long skuId, long quantity, String status) {
        public static ReservationView from(InventoryReservation reservation) {
            return new ReservationView(reservation.getReservationId(), reservation.getSkuId(),
                    reservation.getQuantity(), reservation.getStatus().name());
        }
    }
}
