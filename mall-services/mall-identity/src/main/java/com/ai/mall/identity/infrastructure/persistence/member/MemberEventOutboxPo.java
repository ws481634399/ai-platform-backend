package com.ai.mall.identity.infrastructure.persistence.member;

import java.time.Instant;

/**
 * member_event_outbox 行对象（CHG-0016 Outbox-Lite）。
 *
 * <p>payload_json 为 MemberRegistered 事件 JSON（memberId 以字符串承载）；
 * status 取值 PENDING/DONE。
 */
public class MemberEventOutboxPo {

    private String eventId;
    private String eventType;
    private long memberId;
    private String payloadJson;
    private String status;
    private int retryCount;
    private Instant createdAt;
    private Instant sentAt;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public long getMemberId() { return memberId; }
    public void setMemberId(long memberId) { this.memberId = memberId; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
