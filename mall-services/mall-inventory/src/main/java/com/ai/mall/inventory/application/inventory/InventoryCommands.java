package com.ai.mall.inventory.application.inventory;

/**
 * 库存应用层命令与查询对象。
 */
public final class InventoryCommands {

    private InventoryCommands() {}

    public record InitCommand(long skuId, long totalQuantity) {}

    public record AdjustCommand(long delta, String reason, String businessId) {}

    public record LockCommand(String reservationId, long skuId, long quantity) {}

    public record ReleaseCommand(String reservationId) {}

    public record ConfirmCommand(String reservationId) {}

    public record PageQuery(Integer page, Integer size, Long skuId) {}

    public record BatchQuery(java.util.List<Long> skuIds) {}
}
