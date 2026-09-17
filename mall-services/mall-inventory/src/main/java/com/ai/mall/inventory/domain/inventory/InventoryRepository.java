package com.ai.mall.inventory.domain.inventory;

import java.util.List;
import java.util.Optional;

/**
 * 库存仓储端口。
 */
public interface InventoryRepository {

    Optional<Inventory> findBySkuId(long skuId);

    List<Inventory> findBySkuIds(List<Long> skuIds);

    InventoryPageResult page(InventoryPageQuery query);

    void insert(Inventory inventory);

    boolean update(Inventory inventory);

    void insertLog(InventoryLog log);

    List<InventoryLog> findLogs(InventoryLogQuery query);

    Optional<InventoryReservation> findReservationByReservationId(String reservationId);

    void saveReservation(InventoryReservation reservation);

    /**
     * SQL 条件更新锁定库存：UPDATE inventory_stock SET locked = locked + ?
     * WHERE sku_id = ? AND (total - locked) >= ?
     *
     * @return 受影响行数（1 表示锁定成功，0 表示库存不足）
     */
    int lockStock(long skuId, long quantity);

    /**
     * CHG-0019 CAS 释放预留数量：locked -= quantity WHERE locked >= quantity。
     *
     * @return 受影响行数（0 表示库存账与预留不一致，调用方应整体回滚并报错）
     */
    int releaseStock(long skuId, long quantity);

    /**
     * CHG-0019 CAS 确认扣减：total/locked 同减 quantity WHERE locked >= quantity。
     */
    int deductStock(long skuId, long quantity);

    /**
     * CHG-0019 预留记录状态 CAS：仅 status = fromStatus 时迁移到 toStatus。
     *
     * @return 1 迁移成功；0 已被并发操作改变（终态重复/竞争）
     */
    int casReservationStatus(long reservationPrimaryId, ReservationStatus fromStatus, ReservationStatus toStatus);

    record InventoryPageQuery(Integer page, Integer size, Long skuId) {}

    record InventoryPageResult(List<Inventory> records, long total, int page, int size) {}

    record InventoryLogQuery(Integer page, Integer size, Long skuId) {}
}
