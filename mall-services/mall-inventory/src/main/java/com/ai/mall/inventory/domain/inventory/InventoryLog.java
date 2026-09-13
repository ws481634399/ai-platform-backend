package com.ai.mall.inventory.domain.inventory;

import java.time.Instant;

/**
 * 库存流水实体。
 */
public class InventoryLog {

    private long id;
    private final long skuId;
    private final InventoryOperationType operationType;
    private final long quantity;
    private final long beforeQuantity;
    private final long afterQuantity;
    private final String businessId;
    private final Long operator;
    private final String traceId;
    private final Instant occurredAt;

    public InventoryLog(long skuId, InventoryOperationType operationType, long quantity,
                        long beforeQuantity, long afterQuantity, String businessId,
                        Long operator, String traceId, Instant occurredAt) {
        this.skuId = skuId;
        this.operationType = operationType;
        this.quantity = quantity;
        this.beforeQuantity = beforeQuantity;
        this.afterQuantity = afterQuantity;
        this.businessId = businessId;
        this.operator = operator;
        this.traceId = traceId;
        this.occurredAt = occurredAt;
    }

    public void assignId(long id) {
        this.id = id;
    }

    public long getId() { return id; }
    public long getSkuId() { return skuId; }
    public InventoryOperationType getOperationType() { return operationType; }
    public long getQuantity() { return quantity; }
    public long getBeforeQuantity() { return beforeQuantity; }
    public long getAfterQuantity() { return afterQuantity; }
    public String getBusinessId() { return businessId; }
    public Long getOperator() { return operator; }
    public String getTraceId() { return traceId; }
    public Instant getOccurredAt() { return occurredAt; }
}
