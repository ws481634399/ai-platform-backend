package com.ai.mall.inventory.domain.inventory;

import java.time.Instant;

/**
 * 库存聚合根。以 SKU 为最小单位管理 total/locked 库存。
 */
public class Inventory {

    private long id;
    private final long skuId;
    private long totalQuantity;
    private long lockedQuantity;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    private Inventory(long id, long skuId, long totalQuantity, long lockedQuantity,
                      long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.skuId = skuId;
        this.totalQuantity = totalQuantity;
        this.lockedQuantity = lockedQuantity;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 创建新库存（初始化）。 */
    public static Inventory initialize(long skuId, long totalQuantity) {
        if (totalQuantity < 0) {
            throw InventoryException.quantityInvalid("初始库存不能为负");
        }
        return new Inventory(0L, skuId, totalQuantity, 0L, 0L, null, null);
    }

    /** 持久化重建。 */
    public static Inventory reconstitute(long id, long skuId, long totalQuantity, long lockedQuantity,
                                         long version, Instant createdAt, Instant updatedAt) {
        return new Inventory(id, skuId, totalQuantity, lockedQuantity, version, createdAt, updatedAt);
    }

    /** 调整库存（盘点/人工修正）。 */
    public void adjust(long delta) {
        if (delta == 0) {
            throw InventoryException.quantityInvalid("调整数量不能为 0");
        }
        long newTotal = totalQuantity + delta;
        if (newTotal < 0) {
            throw InventoryException.quantityInvalid("调整后库存不能为负");
        }
        this.totalQuantity = newTotal;
    }

    /** 锁定库存（由 Repository SQL 条件更新保证不超卖，此处仅做数据一致性校验）。 */
    public void lock(long quantity) {
        if (quantity <= 0) {
            throw InventoryException.quantityInvalid("锁定数量必须大于 0");
        }
        if (getAvailableQuantity() < quantity) {
            throw InventoryException.insufficient(skuId);
        }
        this.lockedQuantity += quantity;
    }

    /** 释放库存。 */
    public void release(long quantity) {
        if (this.lockedQuantity < quantity) {
            throw InventoryException.quantityInvalid("释放数量超过已锁定数量");
        }
        this.lockedQuantity -= quantity;
    }

    /** 确认扣减。 */
    public void confirmDeduction(long quantity) {
        if (this.lockedQuantity < quantity) {
            throw InventoryException.quantityInvalid("扣减数量超过已锁定数量");
        }
        this.totalQuantity -= quantity;
        this.lockedQuantity -= quantity;
    }

    public long getAvailableQuantity() {
        return totalQuantity - lockedQuantity;
    }

    public long getId() { return id; }
    public long getSkuId() { return skuId; }
    public long getTotalQuantity() { return totalQuantity; }
    public long getLockedQuantity() { return lockedQuantity; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void assignCreated(long id, Instant now) {
        this.id = id;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }
}
