package com.ai.mall.inventory.domain.inventory;

/**
 * 库存流水操作类型。
 */
public enum InventoryOperationType {
    INIT,
    ADJUST,
    LOCK,
    RELEASE,
    DEDUCT
}
