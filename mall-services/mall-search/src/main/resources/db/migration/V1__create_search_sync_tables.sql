-- CHG-0021 REQ-M5-002：mall_search 库 V1 初始化（重建任务 + 同步失败记录）
-- 兼容 MySQL 8 与 H2(MODE=MySQL) 测试库；时间戳由应用层显式维护。

CREATE TABLE search_index_rebuild_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_no VARCHAR(48) NOT NULL UNIQUE,
  status VARCHAR(16) NOT NULL,              -- PENDING/RUNNING/SUCCESS/FAILED
  total_count INT NOT NULL DEFAULT 0,
  indexed_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  physical_index VARCHAR(96) NOT NULL,
  error_message VARCHAR(1000),
  started_at DATETIME(6),
  finished_at DATETIME(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE INDEX idx_rebuild_status_created ON search_index_rebuild_task (status, created_at);

CREATE TABLE search_sync_failure_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  event_type VARCHAR(16) NOT NULL,          -- UPSERT/DELETE
  status VARCHAR(16) NOT NULL,              -- PENDING/SUCCESS/FAILED_DEAD
  retry_count INT NOT NULL DEFAULT 0,
  max_retries INT NOT NULL DEFAULT 5,
  last_error VARCHAR(1000),
  next_retry_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE INDEX idx_sync_failure_status_next ON search_sync_failure_record (status, next_retry_at);
CREATE INDEX idx_sync_failure_product ON search_sync_failure_record (product_id);
