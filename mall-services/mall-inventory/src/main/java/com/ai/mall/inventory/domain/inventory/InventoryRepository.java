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

    record InventoryPageQuery(Integer page, Integer size, Long skuId) {}

    record InventoryPageResult(List<Inventory> records, long total, int page, int size) {}

    record InventoryLogQuery(Integer page, Integer size, Long skuId) {}
}
