package com.ai.mall.inventory.interfaces.rest.admin.dto;

import com.ai.mall.inventory.domain.inventory.Inventory;
import com.ai.mall.inventory.domain.inventory.InventoryLog;
import java.time.Instant;

/**
 * 库存管理端 DTO。
 */
public final class InventoryDtos {

    private InventoryDtos() {}

    public record InitRequest(long skuId, long totalQuantity) {}

    public record AdjustRequest(long delta, String reason, String businessId) {}

    public record InventoryView(long skuId, long totalQuantity, long lockedQuantity, long availableQuantity) {
        public static InventoryView from(Inventory inventory) {
            return new InventoryView(inventory.getSkuId(), inventory.getTotalQuantity(),
                    inventory.getLockedQuantity(), inventory.getAvailableQuantity());
        }
    }

    public record PageView<T>(java.util.List<T> records, long total, int page, int size) {}

    public record LogView(long id, long skuId, String operationType, long quantity,
                          long beforeQuantity, long afterQuantity, String businessId,
                          Long operator, String traceId, Instant occurredAt) {
        public static LogView from(InventoryLog log) {
            return new LogView(log.getId(), log.getSkuId(), log.getOperationType().name(),
                    log.getQuantity(), log.getBeforeQuantity(), log.getAfterQuantity(),
                    log.getBusinessId(), log.getOperator(), log.getTraceId(), log.getOccurredAt());
        }
    }
}
