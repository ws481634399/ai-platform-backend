package com.ai.mall.common.mq.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.mall.event.Envelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * AbstractIntegrationHandler 六步处理链单测（内存幂等，真实 broker 链路见集成测试）：
 * 版本裁决 WARN+ACK / 重复跳过 / 成功回写 / 异常删占位重试 / DB 异常 fail-closed / 解析失败重试 / MDC 清理。
 */
class AbstractIntegrationHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private TestFixtures.InMemoryIdempotent idempotent;
    private TestFixtures.RecordingHandler handler;

    @BeforeEach
    void setUp() {
        idempotent = new TestFixtures.InMemoryIdempotent();
        handler = new TestFixtures.RecordingHandler(mapper, idempotent);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private MessageExt messageOf(Envelope envelope) throws Exception {
        return TestFixtures.messageExt(envelope, mapper);
    }

    private MessageExt garbageMessage() {
        MessageExt messageExt = new MessageExt();
        messageExt.setBody("not-a-valid-json".getBytes(StandardCharsets.UTF_8));
        messageExt.setMsgId("garbage-msg-id");
        return messageExt;
    }

    @Test
    @DisplayName("①版本裁决：eventVersion=2 超出 maxSupportedVersion=1 → WARN+ACK，handler 未执行且不触碰幂等表")
    void versionRejected_ackWithoutHandling() throws Exception {
        ConsumeConcurrentlyStatus status = handler.processMessage(
                messageOf(TestFixtures.envelope("evt-v2", "TEST_EVENT", 2, "trace-1")));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        assertThat(handler.handled).isEmpty();
        assertThat(idempotent.placeholders).isEmpty();
        assertThat(idempotent.results).isEmpty();
    }

    @Test
    @DisplayName("③幂等占位：重复消息直接 ACK 跳过，handler 不重复执行")
    void duplicateMessage_skipped() throws Exception {
        Envelope first = TestFixtures.envelope("evt-dup", "TEST_EVENT", 1, "trace-1");
        assertThat(handler.processMessage(messageOf(first)))
                .isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        assertThat(handler.handled).hasSize(1);

        // 同一 eventId 第二次投递（结果已回写 SUCCESS）
        assertThat(handler.processMessage(messageOf(first)))
                .isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        assertThat(handler.handled).hasSize(1);
    }

    @Test
    @DisplayName("②④⑤链路：traceId 写 MDC → handle 执行 → 结果回写 SUCCESS → MDC 清理")
    void firstConsumed_handledAndMarkedSuccess() throws Exception {
        Envelope envelope = TestFixtures.envelope("evt-ok", "TEST_EVENT", 1, "trace-xyz");

        ConsumeConcurrentlyStatus status = handler.processMessage(messageOf(envelope));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        assertThat(handler.handled).hasSize(1);
        assertThat(handler.observedTraceIds).containsExactly("trace-xyz");
        assertThat(idempotent.results.get("evt-ok|test-consumer-group"))
                .isEqualTo(IdempotentConsumer.Result.SUCCESS);
        assertThat(MDC.get(AbstractIntegrationHandler.MDC_TRACE_KEY)).isNull();
    }

    @Test
    @DisplayName("Envelope.traceId 缺失 → 自动生成新 ID，消费不阻断")
    void missingTraceId_generated() throws Exception {
        Envelope envelope = TestFixtures.envelope("evt-notrace", "TEST_EVENT", 1, null);

        ConsumeConcurrentlyStatus status = handler.processMessage(messageOf(envelope));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        assertThat(handler.observedTraceIds.get(0)).isNotBlank().hasSize(32);
    }

    @Test
    @DisplayName("handle 抛异常 → 删除幂等占位（允许重试再处理）+ RECONSUME_LATER")
    void handleFailure_deletesPlaceholderAndRequeues() throws Exception {
        handler.failureToThrow = new IllegalStateException("业务处理失败");
        Envelope envelope = TestFixtures.envelope("evt-fail", "TEST_EVENT", 1, "trace-1");

        ConsumeConcurrentlyStatus status = handler.processMessage(messageOf(envelope));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
        assertThat(idempotent.deleteCalls).isEqualTo(1);
        assertThat(idempotent.placeholders).isEmpty();
        assertThat(idempotent.results).doesNotContainKey("evt-fail|test-consumer-group");
    }

    @Test
    @DisplayName("幂等表 DB 异常 → fail-closed 不降级：不执行业务，删除占位后交 RocketMQ 重试")
    void idempotentDbFailure_failClosed() throws Exception {
        idempotent.dbFailure = true;

        ConsumeConcurrentlyStatus status = handler.processMessage(
                messageOf(TestFixtures.envelope("evt-dbdown", "TEST_EVENT", 1, "trace-1")));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
        assertThat(handler.handled).isEmpty();
        assertThat(idempotent.deleteCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("消息体解析失败 → RECONSUME_LATER 保守重试，不触碰幂等表")
    void parseFailure_requeueWithoutIdempotentTouch() throws Exception {
        ConsumeConcurrentlyStatus status = handler.processMessage(garbageMessage());

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
        assertThat(handler.handled).isEmpty();
        assertThat(idempotent.placeholders).isEmpty();
        assertThat(idempotent.deleteCalls).isZero();
    }
}
