package com.ai.mall.common.mq.consumer;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * consumed_event 表 JDBC 幂等实现（INSERT IGNORE 占位 / UPDATE 回写 / DELETE 回滚）。
 * <p>表归属消费方数据库（ mall-inventory 先行建表，requirement-design §5）；
 * 本组件只写 SQL 不建表；表名可配置，默认 consumed_event。</p>
 */
public class ConsumedEventRepository implements IdempotentConsumer {

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;

    public ConsumedEventRepository(JdbcTemplate jdbcTemplate, String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
    }

    @Override
    public TryResult tryConsume(String eventId, String consumerGroup, String eventType,
                                String aggregateId, String traceId) {
        // INSERT IGNORE：唯一键 (event_id, consumer_group) 冲突时静默返回 0 行（MySQL 语义）
        int inserted = jdbcTemplate.update(
                "INSERT IGNORE INTO " + tableName
                        + " (event_id, consumer_group, event_type, aggregate_id, result, trace_id, processed_at)"
                        + " VALUES (?, ?, ?, ?, 'PROCESSING', ?, NOW())",
                eventId, consumerGroup, eventType, aggregateId, traceId);
        return inserted > 0 ? TryResult.FIRST_PROCESSED : TryResult.DUPLICATE;
    }

    @Override
    public void markResult(String eventId, String consumerGroup, Result result) {
        jdbcTemplate.update(
                "UPDATE " + tableName + " SET result = ? WHERE event_id = ? AND consumer_group = ?",
                result.name(), eventId, consumerGroup);
    }

    @Override
    public void deletePlaceholder(String eventId, String consumerGroup) {
        jdbcTemplate.update(
                "DELETE FROM " + tableName + " WHERE event_id = ? AND consumer_group = ?",
                eventId, consumerGroup);
    }

    /** 供集成测试建表使用（DU-BE-005 业务迁移字段与本 DDL 对齐） */
    public static String ddl(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (\n"
                + "  id BIGINT PRIMARY KEY AUTO_INCREMENT,\n"
                + "  event_id VARCHAR(64) NOT NULL,\n"
                + "  consumer_group VARCHAR(64) NOT NULL,\n"
                + "  event_type VARCHAR(64) NOT NULL,\n"
                + "  aggregate_id VARCHAR(64) NULL,\n"
                + "  result VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',\n"
                + "  trace_id VARCHAR(64) NULL,\n"
                + "  processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
                + "  UNIQUE KEY uk_event_group (event_id, consumer_group),\n"
                + "  KEY idx_aggregate (aggregate_id)\n"
                + ")";
    }
}
