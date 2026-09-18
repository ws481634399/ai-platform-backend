package com.ai.mall.inventory.interfaces.rest.admin.dto;

import com.ai.mall.common.web.annotation.StringId;
import com.ai.mall.inventory.domain.inventory.Inventory;
import com.ai.mall.inventory.domain.inventory.InventoryLog;
import java.time.Instant;

/**
 * 库存管理端 DTO。
 *
 * <p>CHG-0015：业务 ID 标注 {@link StringId} 输出字符串；数量保持 number。
 */
public final class InventoryDtos {

    private InventoryDtos() {}

    public record InitRequest(long skuId, long totalQuantity) {}

    public record AdjustRequest(long delta, String reason, String businessId) {}

    /** SKU 展示信息载体（商品服务富化结果，服务间调用失败时字段为 null）。 */
    public record SkuBrief(String productName, String skuCode,
                           java.util.Map<String, String> specifications, String mainImageUrl) {}

    public record InventoryView(@StringId long skuId, long totalQuantity, long lockedQuantity,
                                long availableQuantity, String productName, String skuCode,
                                java.util.Map<String, String> specifications, String mainImageUrl) {
        public static InventoryView from(Inventory inventory) {
            return from(inventory, null);
        }

        public static InventoryView from(Inventory inventory, SkuBrief brief) {
            return new InventoryView(inventory.getSkuId(), inventory.getTotalQuantity(),
                    inventory.getLockedQuantity(), inventory.getAvailableQuantity(),
                    brief == null ? null : brief.productName(),
                    brief == null ? null : brief.skuCode(),
                    brief == null || brief.specifications() == null ? null : brief.specifications(),
                    brief == null ? null : brief.mainImageUrl());
        }
    }

    public record PageView<T>(java.util.List<T> records, long total, int page, int size) {}

    public record LogView(@StringId long id, @StringId long skuId, String operationType, long quantity,
                          long beforeQuantity, long afterQuantity, String businessId,
                          @StringId Long operator, String traceId, Instant occurredAt,
                          String productName, String skuCode,
                          java.util.Map<String, String> specifications) {
        public static LogView from(InventoryLog log) {
            return from(log, null);
        }

        public static LogView from(InventoryLog log, SkuBrief brief) {
            return new LogView(log.getId(), log.getSkuId(), log.getOperationType().name(),
                    log.getQuantity(), log.getBeforeQuantity(), log.getAfterQuantity(),
                    log.getBusinessId(), log.getOperator(), log.getTraceId(), log.getOccurredAt(),
                    brief == null ? null : brief.productName(),
                    brief == null ? null : brief.skuCode(),
                    brief == null || brief.specifications() == null ? null : brief.specifications());
        }
    }
}
