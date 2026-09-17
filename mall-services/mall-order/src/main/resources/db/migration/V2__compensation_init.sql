-- CHG-0019 REQ-M4-004：交易补偿任务表（锁后建单失败 / 支付确认失败 / 取消释放失败）
-- 有界退避重试（30s,1m,2m,5m,10m），达 5 次置 FAILED_DEAD 转人工。

CREATE TABLE compensation_task (
    id BIGINT NOT NULL,
    business_type VARCHAR(48) NOT NULL,
    business_id VARCHAR(64) NOT NULL,
    operation VARCHAR(48) NOT NULL,
    payload TEXT,
    status VARCHAR(16) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 5,
    last_error VARCHAR(1000),
    next_retry_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 同一业务单同一操作只允许一条任务（重试复用，幂等登记）
    UNIQUE KEY uk_business_op (business_type, business_id, operation),
    KEY idx_status_next (status, next_retry_at)
);
