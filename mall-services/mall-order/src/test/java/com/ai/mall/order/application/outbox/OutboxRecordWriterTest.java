package com.ai.mall.order.application.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.ai.mall.event.Envelope;
import com.ai.mall.order.domain.outbox.OutboxEvent;
import com.ai.mall.order.domain.outbox.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** OutboxRecordWriter 单测（TC-001）：写入 PENDING 记录，payload=Envelope JSON，traceId 透传。 */
@ExtendWith(MockitoExtension.class)
class OutboxRecordWriterTest {

    @Mock
    private OutboxEventRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private OutboxRecordWriter writer;

    @BeforeEach
    void setUp() {
        writer = new OutboxRecordWriter(repository, objectMapper);
    }

    @Test
    @DisplayName("append 持久化 PENDING 记录，payload 为 Envelope JSON，traceId 透传")
    void appendSavesPendingEvent() throws Exception {
        JsonNode payload = objectMapper.createObjectNode().put("orderId", "ORD-1");
        Envelope envelope = Envelope.builder()
                .eventId("evt-1").eventType("ORDER_CREATED").eventVersion(1)
                .occurredAt(Instant.now()).producer("mall-order").traceId("trace-1")
                .payload(payload).build();

        writer.append("order-1", "ORDER_CREATED", envelope);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateId()).isEqualTo("order-1");
        assertThat(saved.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(saved.getStatus().name()).isEqualTo("PENDING");
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getTraceId()).isEqualTo("trace-1");
        // payload 列存完整 Envelope JSON
        Envelope roundTrip = objectMapper.readValue(saved.getPayload(), Envelope.class);
        assertThat(roundTrip.getEventId()).isEqualTo("evt-1");
    }

    @Test
    @DisplayName("append 使用 envelope.traceId 写入 trace_id 列")
    void appendStoresEnvelopeTraceId() {
        JsonNode payload = objectMapper.createObjectNode();
        Envelope envelope = Envelope.builder().eventId("e").eventType("T").eventVersion(1)
                .occurredAt(Instant.now()).producer("p").traceId("t-abc").payload(payload).build();

        writer.append("agg", "T", envelope);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTraceId()).isEqualTo("t-abc");
    }
}
