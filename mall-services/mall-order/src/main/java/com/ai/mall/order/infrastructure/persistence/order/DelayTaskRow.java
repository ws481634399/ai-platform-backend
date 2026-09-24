package com.ai.mall.order.infrastructure.persistence.order;

import java.time.Instant;

/**
 * 延迟取消任务管理视图行（union 三源派生）。
 */
public class DelayTaskRow {

    /** FAILED 源订单已消失（LEFT JOIN 落空）时为 null。 */
    private Long orderId;
    private String orderNo;
    private String delayStatus;
    private Instant createdAt;
    private Instant cancelledAt;
    private String lastError;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getDelayStatus() {
        return delayStatus;
    }

    public void setDelayStatus(String delayStatus) {
        this.delayStatus = delayStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
