package com.ai.mall.common.mq.consumer;

import com.ai.mall.event.Envelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.rocketmq.common.message.MessageExt;

/**
 * 测试用内存幂等组件与测试 Handler（验证六步链纯逻辑，Testcontainers 场景另建）。
 */
final class TestFixtures {

    private TestFixtures() {
    }

    /** 内存幂等实现：可注入 DB 故障模拟 fail-closed 行为 */
    static final class InMemoryIdempotent implements IdempotentConsumer {

        final Set<String> placeholders = new HashSet<>();
        final Map<String, Result> results = new HashMap<>();
        boolean dbFailure = false;
        int deleteCalls = 0;

        @Override
        public TryResult tryConsume(String eventId, String consumerGroup, String eventType,
                                    String aggregateId, String traceId) {
            if (dbFailure) {
                throw new IllegalStateException("模拟幂等表 DB 异常");
            }
            String key = eventId + "|" + consumerGroup;
            if (results.containsKey(key) || placeholders.contains(key)) {
                return TryResult.DUPLICATE;
            }
            placeholders.add(key);
            return TryResult.FIRST_PROCESSED;
        }

        @Override
        public void markResult(String eventId, String consumerGroup, Result result) {
            String key = eventId + "|" + consumerGroup;
            placeholders.remove(key);
            results.put(key, result);
        }

        @Override
        public void markSuccessIfProcessing(String eventId, String consumerGroup) {
            // 与 SQL CAS 同语义：仅占位（PROCESSING）时收尾，已显式 SKIPPED 不覆盖
            String key = eventId + "|" + consumerGroup;
            if (placeholders.remove(key)) {
                results.put(key, Result.SUCCESS);
            }
        }

        @Override
        public void deletePlaceholder(String eventId, String consumerGroup) {
            deleteCalls++;
            placeholders.remove(eventId + "|" + consumerGroup);
        }
    }

    @IntegrationEventListener(topic = "aimall-test-topic", eventType = "TEST_EVENT",
            consumerGroup = "test-consumer-group", maxSupportedVersion = 1)
    static final class RecordingHandler extends AbstractIntegrationHandler<JsonNode> {

        final List<Envelope> handled = new ArrayList<>();
        final List<String> observedTraceIds = new ArrayList<>();
        RuntimeException failureToThrow = null;

        RecordingHandler(ObjectMapper objectMapper, IdempotentConsumer idempotentConsumer) {
            super(objectMapper, idempotentConsumer);
        }

        @Override
        protected void handle(Envelope envelope, JsonNode payload) {
            // 记录处理期间的 MDC traceId（验证②步 MDC 写入）
            observedTraceIds.add(org.slf4j.MDC.get(MDC_TRACE_KEY));
            handled.add(envelope);
            if (failureToThrow != null) {
                throw failureToThrow;
            }
        }
    }

    static Envelope envelope(String eventId, String eventType, int version, String traceId) {
        com.fasterxml.jackson.databind.node.ObjectNode payload =
                new ObjectMapper().createObjectNode().put("orderId", "50001");
        return Envelope.builder()
                .eventId(eventId)
                .eventType(eventType)
                .eventVersion(version)
                .occurredAt(Instant.parse("2026-08-01T06:00:00Z"))
                .producer("mall-order")
                .traceId(traceId)
                .payload(payload)
                .build();
    }

    static MessageExt messageExt(Envelope envelope, ObjectMapper mapper) throws Exception {
        MessageExt messageExt = new MessageExt();
        messageExt.setBody(mapper.writeValueAsBytes(envelope));
        messageExt.setMsgId("test-msg-id");
        return messageExt;
    }
}
