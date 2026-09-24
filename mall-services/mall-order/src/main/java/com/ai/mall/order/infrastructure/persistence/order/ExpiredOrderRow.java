package com.ai.mall.order.infrastructure.persistence.order;

/**
 * 超时兜底扫描 Mapper 轻量行（仅 id/order_no 两列）。
 */
public class ExpiredOrderRow {

    private Long id;
    private String orderNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
}
