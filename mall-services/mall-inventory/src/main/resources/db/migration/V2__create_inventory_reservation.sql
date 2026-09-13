-- CHG-0013 库存核心能力：inventory_reservation
-- reservation_id 为幂等键，唯一约束兜底。

CREATE TABLE inventory_reservation (
    id BIGINT NOT NULL,
    reservation_id VARCHAR(128) NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservation_id (reservation_id),
    KEY idx_reservation_sku (sku_id, status)
);
