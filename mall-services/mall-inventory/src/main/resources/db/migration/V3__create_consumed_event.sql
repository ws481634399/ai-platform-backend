-- CHG-0025 STORY-009-05-01：消费幂等表（消费侧最终一致性三件套之消费侧）
-- DDL 与 mall-common-mq ConsumedEventRepository.ddl() 逐字段对齐；
-- 幂等键=(event_id, consumer_group)，先占位后处理，业务处理成功回写 result。

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
    KEY idx_aggregate (aggregate_id)
);
