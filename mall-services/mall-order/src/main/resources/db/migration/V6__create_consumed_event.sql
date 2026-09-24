-- CHG-0025 STORY-009-05-01：消费幂等表（mall-order 侧）
-- DEV-1：PaymentTimeoutCheckHandler 继承 AbstractIntegrationHandler，
-- IdempotentConsumer 由 RocketMQAutoConfiguration fail-closed 自动装配，需实体表承载；
-- 业务幂等仍以订单状态为准，本表仅承担技术去重。DDL 与 inventory V3 同构。

CREATE TABLE consumed_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    consumer_group VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NULL,
    result VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',  -- PROCESSING/SUCCESS/SKIPPED
    trace_id VARCHAR(64) NULL,
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_event_group (event_id, consumer_group),
    -- H2 索引名 schema 级唯一（MySQL 表级），order 库已存在 idx_aggregate，故加前缀
    KEY idx_consumed_aggregate (aggregate_id)
);
