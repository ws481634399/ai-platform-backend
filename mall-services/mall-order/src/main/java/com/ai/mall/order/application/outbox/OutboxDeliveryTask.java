package com.ai.mall.order.application.outbox;

import com.ai.mall.common.mq.producer.IntegrationEventProducer;
import com.ai.mall.event.Envelope;
import com.ai.mall.order.domain.outbox.OutboxEvent;
import com.ai.mall.order.domain.outbox.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 投递任务：扫描到期 PENDING 记录，CAS 抢占后投递到 RocketMQ。
 *
 * <p>并发控制：数据库 CAS 抢占（PENDING→SENDING），多实例天然安全，无需分布式锁。</p>
 * <p>同聚合顺序：每组只投递 created_at 最早一条，前一条 SENT 后下轮才投下一条。</p>
 * <p>失败语义：未超限回 PENDING + 有界退避；超限标 FAILED。MQ 停止/未装配均按失败退避保留 PENDING。</p>
 */
@Component
public class OutboxDeliveryTask {

    private static final Logger log = LoggerFactory.getLogger(OutboxDeliveryTask.class);

    private final OutboxEventRepository repository;
    private final EventRouter eventRouter;
    private final OutboxBackoffPolicy backoff;
    private final ObjectProvider<IntegrationEventProducer> producerProvider;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public OutboxDeliveryTask(OutboxEventRepository repository,
                              EventRouter eventRouter,
                              OutboxBackoffPolicy backoff,
                              ObjectProvider<IntegrationEventProducer> producerProvider,
                              ObjectMapper objectMapper,
                              @Value("${outbox.delivery.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.eventRouter = eventRouter;
        this.backoff = backoff;
        this.producerProvider = producerProvider;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.delivery.fixed-delay:5000}")
    public void run() {
        Instant now = Instant.now();
        List<OutboxEvent> due = repository.findPendingDue(batchSize, now);
        if (due.isEmpty()) {
            return;
        }
        // 同聚合只取 created_at 最早一条（findPendingDue 已按 aggregate_id, created_at 升序）
        List<OutboxEvent> toSend = pickFirstPerAggregate(due);
        int sent = 0;
        int failed = 0;
        for (OutboxEvent event : toSend) {
            try {
                if (deliver(event)) {
                    sent++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                // 单条处理异常不中断整轮调度
                failed++;
                log.error("Outbox 投递处理异常 id={}, eventType={}", event.getId(), event.getEventType(), e);
            }
        }
        if (sent > 0 || failed > 0) {
            log.info("Outbox 投递轮次完成: 扫描={}, 发送成功={}, 失败/退避={}", due.size(), sent, failed);
        }
    }

    private boolean deliver(OutboxEvent event) throws Exception {
        // 1. CAS 抢占
        int claimed = repository.claim(event.getId(), Instant.now());
        if (claimed == 0) {
            return false; // 被其他实例抢走
        }
        try {
            // 2. 反序列化 Envelope
            Envelope envelope = objectMapper.readValue(event.getPayload(), Envelope.class);
            // 3. 路由
            SendTarget target = eventRouter.route(envelope.getEventType());
            // 4. 发送
            IntegrationEventProducer producer = producerProvider.getIfAvailable();
            if (producer == null) {
                throw new IllegalStateException("RocketMQ 生产者未装配（rocketmq.enabled=false 或未就绪），保留 PENDING 待续投");
            }
            if (target.isDelayed()) {
                producer.sendDelay(target.topic(), envelope, target.delayLevel());
            } else if (event.getDelayLevel() > 0) {
                // 延迟事件：级别在业务事务 flush 时落库（STORY-009-04-01），以行内值为准
                producer.sendDelay(target.topic(), envelope, event.getDelayLevel());
            } else {
                producer.sendSync(target.topic(), envelope);
            }
            // 5. 成功
            repository.markSent(event.getId());
            return true;
        } catch (IllegalArgumentException routeError) {
            // 未知 eventType：配置错误，直接 FAILED 不重试
            repository.markFailed(event.getId(), routeError.getMessage());
            log.error("Outbox 未知事件类型，标记 FAILED: id={}, eventType={}", event.getId(), event.getEventType(), routeError);
            return false;
        } catch (Exception sendError) {
            // 发送失败：未超限退避保留 PENDING，超限 FAILED
            int nextRetryCount = event.getRetryCount() + 1;
            if (backoff.shouldFail(nextRetryCount)) {
                repository.markFailed(event.getId(), sendError.getMessage());
                log.error("Outbox 投递超限标记 FAILED: id={}, eventType={}, retries={}",
                        event.getId(), event.getEventType(), nextRetryCount, sendError);
            } else {
                Instant nextRetryAt = backoff.nextRetryAt(nextRetryCount);
                repository.requeueWithBackoff(event.getId(), sendError.getMessage(), nextRetryAt, nextRetryCount);
                log.warn("Outbox 投递失败退避: id={}, eventType={}, retryCount={}, nextRetryAt={}",
                        event.getId(), event.getEventType(), nextRetryCount, nextRetryAt, sendError);
            }
            return false;
        }
    }

    /** 按 aggregateId 分组，每组取首条（列表已按 aggregate_id, created_at 升序）。 */
    private List<OutboxEvent> pickFirstPerAggregate(List<OutboxEvent> events) {
        Map<String, OutboxEvent> firstByAggregate = new LinkedHashMap<>();
        for (OutboxEvent event : events) {
            firstByAggregate.putIfAbsent(event.getAggregateId(), event);
        }
        return new ArrayList<>(firstByAggregate.values());
    }
}
