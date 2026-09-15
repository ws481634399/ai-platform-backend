package com.ai.mall.identity.application.member;

import com.ai.mall.identity.application.port.MemberProvisioner;
import com.ai.mall.identity.domain.model.member.PendingMemberEvent;
import com.ai.mall.identity.domain.repository.MemberEventOutboxRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 会员开通事件投递中继（CHG-0016 Outbox-Lite）。
 *
 * <p>双触发：
 * <ul>
 *   <li>注册事务 afterCommit 立即投递一次（{@link #tryDispatchAfterCommit}），正常路径零延迟；</li>
 *   <li>{@code @Scheduled} 每 30s 扫描 PENDING 兜底（首次延迟 30s），覆盖对端宕机/瞬时网络失败。</li>
 * </ul>
 * 投递成功置 DONE；失败推进 retry_count 并保 PENDING。重试上限 {@value #MAX_RETRIES} 次：
 * 达上限后事件保 PENDING 并打 ERROR 告警日志（不 DLQ、不删除），后续扫描跳过该事件避免刷屏，
 * 可由人工介入或懒补偿（profile-seed）收敛。
 *
 * <p>日志只记录 eventId/次数，不含密码等敏感信息。
 */
@Component
public class MemberProvisionRelay {

    /** 重试上限（requirement-design §2 / story-design §4）。 */
    static final int MAX_RETRIES = 20;

    /** 单次扫描批量上限。 */
    private static final int BATCH_LIMIT = 100;

    private static final Logger log = LoggerFactory.getLogger(MemberProvisionRelay.class);

    private final MemberEventOutboxRepository outbox;
    private final MemberProvisioner provisioner;

    public MemberProvisionRelay(MemberEventOutboxRepository outbox, MemberProvisioner provisioner) {
        this.outbox = outbox;
        this.provisioner = provisioner;
    }

    /** 定时兜底：扫描全部 PENDING 并逐封投递。 */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void dispatchPending() {
        List<PendingMemberEvent> pending;
        try {
            pending = outbox.findPending(BATCH_LIMIT);
        } catch (RuntimeException ex) {
            log.error("member outbox 扫描 PENDING 失败", ex);
            return;
        }
        for (PendingMemberEvent event : pending) {
            attempt(event);
        }
    }

    /**
     * 注册事务提交后立即投递（由 {@link MemberRegistrationService} 注册的同步回调调用）。
     * 任何失败都被吞掉：事件保留 PENDING，交由定时扫描收敛，绝不影响注册接口返回。
     */
    public void tryDispatchAfterCommit(String eventId) {
        try {
            outbox.findPendingByEventId(eventId).ifPresent(this::attempt);
        } catch (RuntimeException ex) {
            log.warn("会员开通事件 afterCommit 投递失败，等待定时重试 eventId={}", eventId, ex);
        }
    }

    /** 投递单封事件：成功 DONE，失败计数+退避（逻辑退避=等下一个 30s 周期）。 */
    private void attempt(PendingMemberEvent pending) {
        String eventId = pending.eventId();
        if (pending.retryCount() >= MAX_RETRIES) {
            // 已达上限：跳过，避免每周期重复 ERROR 刷屏
            return;
        }
        try {
            provisioner.provision(pending.event());
            outbox.markDone(eventId, Instant.now());
            log.debug("会员开通事件投递成功 eventId={}", eventId);
        } catch (RuntimeException ex) {
            int nextRetryCount = pending.retryCount() + 1;
            try {
                outbox.advanceRetry(eventId, nextRetryCount);
            } catch (RuntimeException persistError) {
                log.error("会员开通事件重试计数落库失败 eventId={}", eventId, persistError);
                return;
            }
            if (nextRetryCount >= MAX_RETRIES) {
                log.error("会员开通事件投递达到重试上限 {} 次，事件保 PENDING 待人工处理 eventId={}",
                        MAX_RETRIES, eventId, ex);
            } else {
                log.warn("会员开通事件投递失败，保 PENDING 等待重试 eventId={} retryCount={}",
                        eventId, nextRetryCount, ex);
            }
        }
    }
}
