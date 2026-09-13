package com.ai.mall.inventory.infrastructure.persistence.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("inventory_log")
public class InventoryLogPo {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long skuId;
    private String operationType;
    private Long quantity;
    private Long beforeQuantity;
    private Long afterQuantity;
    private String businessId;
    private Long operator;
    private String traceId;
    private Instant occurredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public Long getQuantity() { return quantity; }
    public void setQuantity(Long quantity) { this.quantity = quantity; }
    public Long getBeforeQuantity() { return beforeQuantity; }
    public void setBeforeQuantity(Long beforeQuantity) { this.beforeQuantity = beforeQuantity; }
    public Long getAfterQuantity() { return afterQuantity; }
    public void setAfterQuantity(Long afterQuantity) { this.afterQuantity = afterQuantity; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }
    public Long getOperator() { return operator; }
    public void setOperator(Long operator) { this.operator = operator; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
