-- CHG-0025 M7 STORY-009-02-01：Outbox 可靠投递——业务事件发件箱表
-- 业务事务内原子写入；独立调度任务扫描 PENDING 投递到 RocketMQ。
CREATE TABLE outbox_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  aggregate_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME NULL,
  trace_id VARCHAR(64) NULL,
  last_error VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sent_at DATETIME NULL,
  KEY idx_status_retry (status, next_retry_at),
  KEY idx_aggregate (aggregate_id, created_at)
);
