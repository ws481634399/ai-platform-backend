package com.ai.mall.order.infrastructure.persistence.outbox;

import com.ai.mall.order.domain.outbox.OutboxEvent;
import com.ai.mall.order.domain.outbox.OutboxEventRepository;
import com.ai.mall.order.domain.outbox.OutboxStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboxEventRepository MyBatis + H2(Flyway) 集成测试（TC-001~006）：
 * CAS 抢占、状态流转、退避重投、人工重投、到期待投递查询、分页筛选。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(statements = "DELETE FROM outbox_event")
class OutboxEventRepositoryIntegrationTest {

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private OutboxEvent newEvent(String aggregateId, String eventType, OutboxStatus status,
                                 int retryCount, Instant nextRetryAt) {
        return OutboxEvent.reconstitute(null, aggregateId, eventType, "{\"eventId\":\"x\"}",
                status, retryCount, nextRetryAt, "trace-1", null, Instant.now(), null);
    }

    @Test
    @DisplayName("TC-001：save 持久化 PENDING 记录，Flyway V4 表与索引存在")
    void saveAndTableExists() {
        OutboxEvent e = newEvent("ord-1", "ORDER_CREATED", OutboxStatus.PENDING, 0, null);
        repository.save(e);
        assertThat(e.getId()).isNotNull();

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE id = ?", Integer.class, e.getId());
        assertThat(count).isEqualTo(1);

        // 索引存在性
        List<String> indexes = jdbc.queryForList(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = 'OUTBOX_EVENT'",
                String.class);
        assertThat(indexes).anyMatch(i -> i.toUpperCase().contains("IDX_STATUS_RETRY"));
        assertThat(indexes).anyMatch(i -> i.toUpperCase().contains("IDX_AGGREGATE"));
    }

    @Test
    @DisplayName("claim CAS：PENDING 可抢占置 SENDING；非 PENDING 返回 0")
    void claimCas() {
        OutboxEvent e = newEvent("ord-1", "ORDER_CREATED", OutboxStatus.PENDING, 0, null);
        repository.save(e);

        int claimed = repository.claim(e.getId(), Instant.now());
        assertThat(claimed).isEqualTo(1);
        assertThat(repository.findById(e.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENDING);

        // 重复抢占返回 0
        int claimedAgain = repository.claim(e.getId(), Instant.now());
        assertThat(claimedAgain).isZero();
    }

    @Test
    @DisplayName("markSent：SENDING → SENT")
    void markSentFromSending() {
        OutboxEvent e = newEvent("ord-1", "ORDER_CREATED", OutboxStatus.PENDING, 0, null);
        repository.save(e);
        repository.claim(e.getId(), Instant.now());

        int updated = repository.markSent(e.getId());
        assertThat(updated).isEqualTo(1);
        assertThat(repository.findById(e.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    @DisplayName("requeueWithBackoff：SENDING → PENDING，retry_count 与 next_retry_at 更新")
    void requeueWithBackoff() {
        OutboxEvent e = newEvent("ord-1", "ORDER_CREATED", OutboxStatus.PENDING, 0, null);
        repository.save(e);
        repository.claim(e.getId(), Instant.now());
        Instant nextRetry = Instant.now().plusSeconds(30);

        int updated = repository.requeueWithBackoff(e.getId(), "broker down", nextRetry, 1);
        assertThat(updated).isEqualTo(1);
        OutboxEvent reloaded = repository.findById(e.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.getRetryCount()).isEqualTo(1);
        assertThat(reloaded.getNextRetryAt().toEpochMilli()).isEqualTo(nextRetry.toEpochMilli());
        assertThat(reloaded.getLastError()).contains("broker down");
    }

    @Test
    @DisplayName("TC-006：markFailed：SENDING → FAILED 并记录 lastError")
    void markFailed() {
        OutboxEvent e = newEvent("ord-1", "ORDER_CREATED", OutboxStatus.PENDING, 0, null);
        repository.save(e);
        repository.claim(e.getId(), Instant.now());

        int updated = repository.markFailed(e.getId(), "exhausted");
        assertThat(updated).isEqualTo(1);
        OutboxEvent reloaded = repository.findById(e.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(reloaded.getLastError()).contains("exhausted");
    }

    @Test
    @DisplayName("TC-007：resetForRetry：FAILED → PENDING，重置 retry_count/next_retry_at/lastError")
    void resetForRetry() {
        OutboxEvent e = newEvent("ord-1", "ORDER_CREATED", OutboxStatus.FAILED, 5, Instant.now());
        repository.save(e);

        int updated = repository.resetForRetry(e.getId());
        assertThat(updated).isEqualTo(1);
        OutboxEvent reloaded = repository.findById(e.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.getRetryCount()).isZero();
        assertThat(reloaded.getNextRetryAt()).isNull();
        assertThat(reloaded.getLastError()).isNull();
    }

    @Test
    @DisplayName("TC-003/004：findPendingDue 只返回到期 PENDING（next_retry_at 为 null 或 <= now），按聚合+创建时间排序")
    void findPendingDue() throws InterruptedException {
        OutboxEvent due1 = newEvent("ord-1", "ORDER_CREATED", OutboxStatus.PENDING, 0, null);
        OutboxEvent due2 = newEvent("ord-2", "ORDER_CREATED", OutboxStatus.PENDING, 0, Instant.now().minusSeconds(10));
        OutboxEvent future = newEvent("ord-3", "ORDER_CREATED", OutboxStatus.PENDING, 0, Instant.now().plusSeconds(60));
        OutboxEvent sent = newEvent("ord-4", "ORDER_CREATED", OutboxStatus.SENT, 0, null);
        repository.save(due1);
        repository.save(due2);
        repository.save(future);
        repository.save(sent);

        List<OutboxEvent> due = repository.findPendingDue(100, Instant.now());
        assertThat(due).extracting(OutboxEvent::getAggregateId)
                .containsExactly("ord-1", "ord-2");
        assertThat(due).noneMatch(e -> e.getAggregateId().equals("ord-3"));
    }

    @Test
    @DisplayName("page：按 status/eventType/aggregateId 筛选并分页")
    void pageWithFilters() {
        repository.save(newEvent("ord-1", "ORDER_CREATED", OutboxStatus.PENDING, 0, null));
        repository.save(newEvent("ord-1", "ORDER_CREATED", OutboxStatus.FAILED, 3, null));
        repository.save(newEvent("ord-2", "ORDER_CANCELLED", OutboxStatus.PENDING, 0, null));

        var failedPage = repository.page("FAILED", null, null, 1, 10);
        assertThat(failedPage.records()).hasSize(1);
        assertThat(failedPage.records().get(0).getStatus()).isEqualTo(OutboxStatus.FAILED);

        var order1Page = repository.page(null, null, "ord-1", 1, 10);
        assertThat(order1Page.records()).hasSize(2);
    }

    @Test
    @DisplayName("TC-003：V5 delay_level 列存在默认 0；save/reload 携带延迟级别")
    void delayLevelColumn() {
        OutboxEvent delayed = OutboxEvent.reconstitute(null, "ord-delay", "PAYMENT_TIMEOUT_CHECK",
                "{\"eventId\":\"x\"}", OutboxStatus.PENDING, 0, null, "trace-1", null,
                Instant.now(), null, 16);
        repository.save(delayed);

        OutboxEvent reloaded = repository.findById(delayed.getId()).orElseThrow();
        assertThat(reloaded.getDelayLevel()).isEqualTo(16);

        // 既有写入路径不显式带级别时列默认 0
        jdbc.update("INSERT INTO outbox_event(aggregate_id,event_type,payload) VALUES ('a','T','{}')");
        Integer level = jdbc.queryForObject(
                "SELECT delay_level FROM outbox_event WHERE aggregate_id = 'a'", Integer.class);
        assertThat(level).isZero();
    }
}
