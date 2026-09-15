package com.ai.mall.identity.domain.model.member;

/**
 * outbox 待投递事件视图（CHG-0016）：relay 扫描 PENDING 行时的领域模型，
 * 携带投递控制面字段（重试计数）与事件本体。
 *
 * @param event      事件本体（由 payload_json 还原）
 * @param retryCount 已重试次数（达到上限后仅告警，不 DLQ）
 */
public record PendingMemberEvent(MemberRegisteredEvent event, int retryCount) {

    public String eventId() {
        return event.eventId();
    }
}
