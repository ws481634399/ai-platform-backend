package com.ai.mall.order.application.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.common.mq.producer.IntegrationEventProducer;
import com.ai.mall.event.Envelope;
import com.ai.mall.event.EventTags;
import com.ai.mall.event.EventTopics;
import com.ai.mall.order.domain.outbox.OutboxEvent;
import com.ai.mall.order.domain.outbox.OutboxEventRepository;
import com.ai.mall.order.domain.outbox.OutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * OutboxDeliveryTask 投递任务单测（TC-002~006）：
 * CAS 抢占、成功 SENT、失败退避、超限 FAILED、同聚合顺序、未知 eventType FAILED、生产者未装配退避。
 */
@ExtendWith(MockitoExtension.class)
class OutboxDeliveryTaskTest {

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private ObjectProvider<IntegrationEventProducer> producerProvider;
    @Mock
    private IntegrationEventProducer producer;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final EventRouter eventRouter;
    private final OutboxBackoffPolicy backoff = new OutboxBackoffPolicy(Duration.ofSeconds(1), 2.0, Duration.ofMinutes(5), 3);

    private OutboxDeliveryTask task;

    OutboxDeliveryTaskTest() {
        eventRouter = new EventRouter();
        eventRouter.initDefaultRoutes(); // @PostConstruct 仅在 Spring 容器中触发
    }

    @BeforeEach
    void setUp() {
        task = new OutboxDeliveryTask(repository, eventRouter, backoff, producerProvider, objectMapper, 100);
    }

    private OutboxEvent event(Long id, String aggregateId, String eventType, int retryCount) throws Exception {
        Envelope env = Envelope.builder().eventId("evt-" + id).eventType(eventType).eventVersion(1)
                .occurredAt(Instant.now()).producer("mall-order").traceId("t-" + id)
                .payload(objectMapper.createObjectNode()).build();
        return OutboxEvent.reconstitute(id, aggregateId, eventType, objectMapper.writeValueAsString(env),
                OutboxStatus.PENDING, retryCount, null, "t-" + id, null, Instant.now(), null);
    }

    @Test
    @DisplayName("TC-002：到期 PENDING 抢占成功 + 发送成功 → markSent；keys=eventId（producer 收到正确 envelope）")
    void deliverSuccessMarksSent() throws Exception {
        OutboxEvent e = event(1L, "ord-1", EventTags.ORDER_CREATED, 0);
        when(repository.findPendingDue(anyInt(), any())).thenReturn(List.of(e));
        when(repository.claim(eq(1L), any())).thenReturn(1);
        when(producerProvider.getIfAvailable()).thenReturn(producer);

        task.run();

        verify(producer).sendSync(eq(EventTopics.AIMALL_ORDER_EVENTS), any(Envelope.class));
        verify(repository).markSent(1L);
        verify(repository, never()).requeueWithBackoff(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("TC-003：发送失败且未超限 → 回 PENDING + 退避（retry_count+1，next_retry_at），不标 FAILED")
    void deliverFailureRequeuesWithBackoff() throws Exception {
        OutboxEvent e = event(2L, "ord-1", EventTags.ORDER_CREATED, 0);
        when(repository.findPendingDue(anyInt(), any())).thenReturn(List.of(e));
        when(repository.claim(eq(2L), any())).thenReturn(1);
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        org.mockito.Mockito.doThrow(new RuntimeException("broker down")).when(producer).sendSync(anyString(), any());

        task.run();

        verify(repository).requeueWithBackoff(eq(2L), anyString(), any(Instant.class), eq(1));
        verify(repository, never()).markSent(any());
        verify(repository, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("TC-006：持续失败超 maxRetries → 标 FAILED + lastError")
    void deliverFailureExceededMarksFailed() throws Exception {
        OutboxEvent e = event(3L, "ord-1", EventTags.ORDER_CREATED, 2); // next=3 == maxRetries
        when(repository.findPendingDue(anyInt(), any())).thenReturn(List.of(e));
        when(repository.claim(eq(3L), any())).thenReturn(1);
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        org.mockito.Mockito.doThrow(new RuntimeException("broker down")).when(producer).sendSync(anyString(), any());

        task.run();

        verify(repository).markFailed(eq(3L), anyString());
        verify(repository, never()).requeueWithBackoff(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("TC-005：同聚合多条只投最早一条（每组首条），其余跳过")
    void sameAggregatePicksFirstOnly() throws Exception {
        OutboxEvent e1 = event(1L, "ord-1", EventTags.ORDER_CREATED, 0);
        OutboxEvent e2 = event(2L, "ord-1", EventTags.PAYMENT_SUCCEEDED, 0);
        OutboxEvent e3 = event(3L, "ord-2", EventTags.ORDER_CREATED, 0);
        when(repository.findPendingDue(anyInt(), any())).thenReturn(List.of(e1, e2, e3));
        when(repository.claim(any(), any())).thenReturn(1);
        when(producerProvider.getIfAvailable()).thenReturn(producer);

        task.run();

        // e1(ord-1 首条) 与 e3(ord-2 首条) 被投递；e2 被跳过
        verify(repository).claim(eq(1L), any());
        verify(repository).claim(eq(3L), any());
        verify(repository, never()).claim(eq(2L), any());
        verify(repository, times(2)).markSent(any());
    }

    @Test
    @DisplayName("CAS 抢占失败（被其他实例抢走）→ 跳过该条，不发送不更新")
    void claimFailedSkips() throws Exception {
        OutboxEvent e = event(1L, "ord-1", EventTags.ORDER_CREATED, 0);
        when(repository.findPendingDue(anyInt(), any())).thenReturn(List.of(e));
        when(repository.claim(eq(1L), any())).thenReturn(0);

        task.run();

        verify(producerProvider, never()).getIfAvailable();
        verify(repository, never()).markSent(any());
        verify(repository, never()).requeueWithBackoff(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("未知 eventType → 直接 FAILED，不重试")
    void unknownEventTypeMarksFailed() throws Exception {
        OutboxEvent e = event(1L, "ord-1", "UNKNOWN_EVENT", 0);
        when(repository.findPendingDue(anyInt(), any())).thenReturn(List.of(e));
        when(repository.claim(eq(1L), any())).thenReturn(1);

        task.run();

        verify(repository).markFailed(eq(1L), anyString());
        verify(producerProvider, never()).getIfAvailable();
        verify(repository, never()).requeueWithBackoff(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("rocketmq.enabled=false 生产者未装配 → 按失败退避保留 PENDING（不标 FAILED）")
    void producerMissingRequeues() throws Exception {
        OutboxEvent e = event(1L, "ord-1", EventTags.ORDER_CREATED, 0);
        when(repository.findPendingDue(anyInt(), any())).thenReturn(List.of(e));
        when(repository.claim(eq(1L), any())).thenReturn(1);
        when(producerProvider.getIfAvailable()).thenReturn(null);

        task.run();

        verify(repository).requeueWithBackoff(eq(1L), anyString(), any(Instant.class), eq(1));
        verify(repository, never()).markSent(any());
    }

    @Test
    @DisplayName("无到期记录 → 空轮不操作")
    void noDueRecordsNoOp() {
        when(repository.findPendingDue(anyInt(), any())).thenReturn(List.of());

        task.run();

        verify(repository, never()).claim(any(), any());
        assertThat(eventRouter.route(EventTags.ORDER_CREATED).topic()).isEqualTo(EventTopics.AIMALL_ORDER_EVENTS);
    }
}
