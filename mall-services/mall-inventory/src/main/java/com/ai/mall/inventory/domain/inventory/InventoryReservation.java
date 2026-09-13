package com.ai.mall.inventory.domain.inventory;

import java.time.Instant;

/**
 * 库存预留实体。
 */
public class InventoryReservation {

    private long id;
    private final String reservationId;
    private final long skuId;
    private final long quantity;
    private ReservationStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public InventoryReservation(String reservationId, long skuId, long quantity) {
        this.reservationId = reservationId;
        this.skuId = skuId;
        this.quantity = quantity;
        this.status = ReservationStatus.LOCKED;
    }

    /** 持久化重建。 */
    public static InventoryReservation reconstitute(long id, String reservationId, long skuId, long quantity,
                                                    ReservationStatus status, Instant createdAt, Instant updatedAt) {
        InventoryReservation r = new InventoryReservation(reservationId, skuId, quantity);
        r.id = id;
        r.status = status;
        r.createdAt = createdAt;
        r.updatedAt = updatedAt;
        return r;
    }

    public void release() {
        if (this.status == ReservationStatus.RELEASED) {
            return;
        }
        if (this.status != ReservationStatus.LOCKED) {
            throw InventoryException.reservationInvalidState(reservationId, status);
        }
        this.status = ReservationStatus.RELEASED;
    }

    public void confirmDeduction() {
        if (this.status == ReservationStatus.DEDUCTED) {
            return;
        }
        if (this.status != ReservationStatus.LOCKED) {
            throw InventoryException.reservationInvalidState(reservationId, status);
        }
        this.status = ReservationStatus.DEDUCTED;
    }

    public void assignId(long id) {
        this.id = id;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public void assignCreated(Instant now) {
        this.createdAt = now;
        this.updatedAt = now;
    }

    public long getId() { return id; }
    public String getReservationId() { return reservationId; }
    public long getSkuId() { return skuId; }
    public long getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
