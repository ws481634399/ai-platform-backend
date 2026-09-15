package com.ai.mall.identity.domain.repository;

import com.ai.mall.identity.domain.model.member.MemberRegisteredEvent;
import com.ai.mall.identity.domain.model.member.PendingMemberEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 会员事件 outbox 仓储端口（CHG-0016 Outbox-Lite）。
 *
 * <p>注册事务内 {@link #append}（与 member_user 同库同事务）；relay 投递成功
 * {@link #markDone}，失败 {@link #advanceRetry}。无跨库事务、无 MQ。
 */
public interface MemberEventOutboxRepository {

    /** 同事务追加 PENDING 事件（payload_json 由基础设施序列化）。 */
    void append(MemberRegisteredEvent event);

    /** 按创建顺序取出不超过 limit 条 PENDING 事件（@Scheduled 扫描）。 */
    List<PendingMemberEvent> findPending(int limit);

    /** 按事件 ID 加载 PENDING 事件（afterCommit 立即投递用）；不存在或已 DONE 返回 empty。 */
    Optional<PendingMemberEvent> findPendingByEventId(String eventId);

    /** 投递成功：置 DONE 并记录发送时刻。 */
    void markDone(String eventId, Instant sentAt);

    /** 投递失败：推进重试计数（保 PENDING，等待下一个 30s 扫描周期）。 */
    void advanceRetry(String eventId, int retryCount);
}
