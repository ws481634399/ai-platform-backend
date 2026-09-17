package com.ai.mall.order.infrastructure.persistence.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** orders 表 PO（CHG-0019）。 */
@TableName("orders")
public class OrderPo {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderNo;
    private Long memberId;
    private String status;
    private String source;
    private Long goodsAmount;
    private Long discountAmount;
    private Long freightAmount;
    private Long payAmount;
    private String receiverName;
    private String receiverPhone;
    private String receiverProvince;
    private String receiverCity;
    private String receiverDistrict;
    private String receiverDetailAddress;
    private String receiverPostalCode;
    private String deliveryCompany;
    private String trackingNo;
    private String cancelReason;
    private String submitToken;
    private Instant paidAt;
    private Instant cancelledAt;
    private Instant shippedAt;
    private Instant completedAt;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getGoodsAmount() { return goodsAmount; }
    public void setGoodsAmount(Long goodsAmount) { this.goodsAmount = goodsAmount; }
    public Long getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(Long discountAmount) { this.discountAmount = discountAmount; }
    public Long getFreightAmount() { return freightAmount; }
    public void setFreightAmount(Long freightAmount) { this.freightAmount = freightAmount; }
    public Long getPayAmount() { return payAmount; }
    public void setPayAmount(Long payAmount) { this.payAmount = payAmount; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }
    public String getReceiverProvince() { return receiverProvince; }
    public void setReceiverProvince(String receiverProvince) { this.receiverProvince = receiverProvince; }
    public String getReceiverCity() { return receiverCity; }
    public void setReceiverCity(String receiverCity) { this.receiverCity = receiverCity; }
    public String getReceiverDistrict() { return receiverDistrict; }
    public void setReceiverDistrict(String receiverDistrict) { this.receiverDistrict = receiverDistrict; }
    public String getReceiverDetailAddress() { return receiverDetailAddress; }
    public void setReceiverDetailAddress(String receiverDetailAddress) { this.receiverDetailAddress = receiverDetailAddress; }
    public String getReceiverPostalCode() { return receiverPostalCode; }
    public void setReceiverPostalCode(String receiverPostalCode) { this.receiverPostalCode = receiverPostalCode; }
    public String getDeliveryCompany() { return deliveryCompany; }
    public void setDeliveryCompany(String deliveryCompany) { this.deliveryCompany = deliveryCompany; }
    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getSubmitToken() { return submitToken; }
    public void setSubmitToken(String submitToken) { this.submitToken = submitToken; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public Instant getShippedAt() { return shippedAt; }
    public void setShippedAt(Instant shippedAt) { this.shippedAt = shippedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
