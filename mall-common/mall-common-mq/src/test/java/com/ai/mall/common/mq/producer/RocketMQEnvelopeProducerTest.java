package com.ai.mall.common.mq.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.event.Envelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * RocketMQEnvelopeProducer 纯逻辑单测：消息构造契约（tag=eventType/keys=eventId/body=Envelope JSON）
 * 与 traceId 注入策略（显式值 &gt; MDC &gt; 自动生成）、delayLevel 参数校验。
 * 真实 broker 收发链路见 RocketMQBrokerIntegrationTest。
 */
class RocketMQEnvelopeProducerTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final DefaultMQProducer rawProducer = mock(DefaultMQProducer.class);
    private RocketMQEnvelopeProducer producer;

    @BeforeEach
    void setUp() {
        producer = new RocketMQEnvelopeProducer(rawProducer, mapper);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private Envelope envelope(String traceId) {
        ObjectNode payload = mapper.createObjectNode().put("orderId", "50001");
        Envelope.Builder builder = Envelope.builder()
                .eventId("evt-" + UUID.randomUUID())
                .eventType("ORDER_CREATED")
                .eventVersion(1)
                .occurredAt(Instant.parse("2026-08-01T06:00:00Z"))
                .producer("mall-order")
                .payload(payload);
        return traceId == null ? builder.build() : builder.traceId(traceId).build();
    }

    @Test
    @DisplayName("buildMessage：tag=eventType、keys=eventId、body 可反序列化回 Envelope")
    void buildMessage_contractFields() throws Exception {
        Envelope source = envelope("trace-abc");

        Message message = producer.buildMessage("aimall-order-events", source);

        assertThat(message.getTopic()).isEqualTo("aimall-order-events");
        assertThat(message.getTags()).isEqualTo("ORDER_CREATED");
        assertThat(message.getKeys()).isEqualTo(source.getEventId());
        Envelope decoded = mapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8), Envelope.class);
        assertThat(decoded.getEventId()).isEqualTo(source.getEventId());
        assertThat(decoded.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(decoded.getEventVersion()).isEqualTo(1);
        assertThat(decoded.getOccurredAt()).isEqualTo(Instant.parse("2026-08-01T06:00:00Z"));
        assertThat(decoded.getProducer()).isEqualTo("mall-order");
        assertThat(decoded.getTraceId()).isEqualTo("trace-abc");
        assertThat(decoded.getPayload().get("orderId").asText()).isEqualTo("50001");
    }

    @Test
    @DisplayName("traceId 缺失时从 MDC 读取当前链路值")
    void buildMessage_traceIdFromMdc() throws Exception {
        MDC.put("traceId", "mdc-trace-42");
        Envelope source = envelope(null);

        Message message = producer.buildMessage("aimall-order-events", source);
        Envelope decoded = mapper.readValue(message.getBody(), Envelope.class);

        assertThat(decoded.getTraceId()).isEqualTo("mdc-trace-42");
    }

    @Test
    @DisplayName("traceId 与 MDC 均缺失时自动生成且消费不阻断")
    void buildMessage_traceIdGenerated() throws Exception {
        Envelope source = envelope(null);

        Message message = producer.buildMessage("aimall-order-events", source);
        Envelope decoded = mapper.readValue(message.getBody(), Envelope.class);

        assertThat(decoded.getTraceId()).isNotBlank().hasSize(32);
    }

    @Test
    @DisplayName("sendDelay：delayLevel 越界（0/19）快速失败，1/18 边界打到消息 delayTimeLevel 属性")
    void sendDelay_validatesLevelRange() throws Exception {
        Envelope source = envelope("trace-abc");
        when(rawProducer.send(any(Message.class))).thenReturn(new SendResult());

        assertThatThrownBy(() -> producer.sendDelay("aimall-order-delay", source, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delayLevel");
        assertThatThrownBy(() -> producer.sendDelay("aimall-order-delay", source, 19))
                .isInstanceOf(IllegalArgumentException.class);

        producer.sendDelay("aimall-order-delay", source, 1);
        producer.sendDelay("aimall-order-delay", envelope("trace-abc"), 18);
        // 显式类型见证：send(Message) 与 send(Collection<Message>) 重载间消歧
        verify(rawProducer).send(org.mockito.ArgumentMatchers.<Message>argThat(m -> m.getDelayTimeLevel() == 1));
        verify(rawProducer).send(org.mockito.ArgumentMatchers.<Message>argThat(m -> m.getDelayTimeLevel() == 18));
    }

    @Test
    @DisplayName("sendSync 失败上抛 IllegalStateException：由调用方（Outbox/降级路径）决定退避")
    void sendSync_failureThrows() throws Exception {
        Envelope source = envelope("trace-abc");
        when(rawProducer.send(any(Message.class))).thenThrow(new RuntimeException("模拟 broker 不可达"));

        assertThatThrownBy(() -> producer.sendSync("aimall-order-events", source))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(source.getEventId());
    }
}
