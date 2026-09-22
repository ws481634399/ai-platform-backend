package com.ai.mall.common.mq.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * ConsumedEventRepository 真实 SQL 语义测试（H2 MySQL 兼容模式验证 INSERT IGNORE 占位/回写/删除）。
 * 建表 DDL 与 DU-BE-005 业务迁移对齐（ConsumedEventRepository.ddl）。
 */
class ConsumedEventRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private ConsumedEventRepository repository;

    @BeforeEach
    void setUp() {
        // 每个用例独立内存库，避免占位状态串扰
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(ConsumedEventRepository.ddl("consumed_event"));
        repository = new ConsumedEventRepository(jdbcTemplate, "consumed_event");
    }

    @Test
    @DisplayName("tryConsume：首占 FIRST_PROCESSED，同 eventId+group 再次占位 → DUPLICATE")
    void tryConsume_firstThenDuplicate() {
        assertThat(repository.tryConsume("evt-1", "inventory-consumer-group", "ORDER_CREATED", "50001", "t1"))
                .isEqualTo(IdempotentConsumer.TryResult.FIRST_PROCESSED);
        assertThat(repository.tryConsume("evt-1", "inventory-consumer-group", "ORDER_CREATED", "50001", "t1"))
                .isEqualTo(IdempotentConsumer.TryResult.DUPLICATE);
    }

    @Test
    @DisplayName("幂等键隔离：同 eventId 不同 consumerGroup 各自占位（每个消费组独立消费）")
    void tryConsume_isolatedByConsumerGroup() {
        assertThat(repository.tryConsume("evt-1", "group-a", "ORDER_CREATED", "50001", "t1"))
                .isEqualTo(IdempotentConsumer.TryResult.FIRST_PROCESSED);
        assertThat(repository.tryConsume("evt-1", "group-b", "ORDER_CREATED", "50001", "t1"))
                .isEqualTo(IdempotentConsumer.TryResult.FIRST_PROCESSED);
    }

    @Test
    @DisplayName("markResult：SUCCESS 回写持久化，重复消息仍判定 DUPLICATE")
    void markResult_persisted() {
        repository.tryConsume("evt-2", "group-a", "ORDER_CANCELLED", "50002", "t2");
        repository.markResult("evt-2", "group-a", IdempotentConsumer.Result.SUCCESS);

        String result = jdbcTemplate.queryForObject(
                "SELECT result FROM consumed_event WHERE event_id = ? AND consumer_group = ?",
                String.class, "evt-2", "group-a");
        assertThat(result).isEqualTo("SUCCESS");
        assertThat(repository.tryConsume("evt-2", "group-a", "ORDER_CANCELLED", "50002", "t2"))
                .isEqualTo(IdempotentConsumer.TryResult.DUPLICATE);
    }

    @Test
    @DisplayName("deletePlaceholder：异常回滚删除占位后，同事件可重新占位（允许 RocketMQ 重试再处理）")
    void deletePlaceholder_allowsReconsume() {
        assertThat(repository.tryConsume("evt-3", "group-a", "PAYMENT_SUCCEEDED", "50003", "t3"))
                .isEqualTo(IdempotentConsumer.TryResult.FIRST_PROCESSED);

        repository.deletePlaceholder("evt-3", "group-a");

        assertThat(repository.tryConsume("evt-3", "group-a", "PAYMENT_SUCCEEDED", "50003", "t3"))
                .isEqualTo(IdempotentConsumer.TryResult.FIRST_PROCESSED);
    }
}
