-- CHG-0013 库存核心能力：inventory_stock / inventory_log
-- 库存与商品独立上下文，仅存 skuId，不复制商品主数据。

CREATE TABLE inventory_stock (
    id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    total_quantity BIGINT NOT NULL DEFAULT 0,
    locked_quantity BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_id (sku_id),
    KEY idx_sku_id (sku_id)
);

CREATE TABLE inventory_log (
    id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    quantity BIGINT NOT NULL,
    before_quantity BIGINT NOT NULL,
    after_quantity BIGINT NOT NULL,
    business_id VARCHAR(128) NULL,
    operator BIGINT NULL,
    trace_id VARCHAR(64) NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_log_sku (sku_id, occurred_at),
    KEY idx_log_operation (operation_type),
    KEY idx_log_business (business_id)
);
