package com.ai.mall.inventory.infrastructure.persistence.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("inventory_stock")
public class InventoryPo {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long skuId;
    private Long totalQuantity;
    private Long lockedQuantity;
    private Instant createdAt;
    private Instant updatedAt;
    private Long createdBy;
    private Long updatedBy;
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Long getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Long totalQuantity) { this.totalQuantity = totalQuantity; }
    public Long getLockedQuantity() { return lockedQuantity; }
    public void setLockedQuantity(Long lockedQuantity) { this.lockedQuantity = lockedQuantity; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
