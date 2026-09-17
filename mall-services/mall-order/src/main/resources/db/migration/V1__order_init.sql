-- CHG-0019 M4 订单交易闭环：订单主表 / 订单项快照 / 订单状态历史
-- H2（MODE=MySQL）与 MySQL 双兼容：不使用 JSON 专有类型、不使用生成列、索引内联。

CREATE TABLE orders (
    id BIGINT NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    member_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    source VARCHAR(16) NOT NULL,
    goods_amount BIGINT NOT NULL,
    discount_amount BIGINT NOT NULL DEFAULT 0,
    freight_amount BIGINT NOT NULL DEFAULT 0,
    pay_amount BIGINT NOT NULL,
    receiver_name VARCHAR(32) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    receiver_province VARCHAR(64) NOT NULL,
    receiver_city VARCHAR(64) NOT NULL,
    receiver_district VARCHAR(64) NOT NULL,
    receiver_detail_address VARCHAR(128) NOT NULL,
    receiver_postal_code VARCHAR(16),
    delivery_company VARCHAR(64),
    tracking_no VARCHAR(64),
    cancel_reason VARCHAR(255),
    submit_token VARCHAR(64),
    paid_at DATETIME(6),
    cancelled_at DATETIME(6),
    shipped_at DATETIME(6),
    completed_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    -- 双层幂等第二道：同会员同令牌只能落一单（submit_token 可空，多 NULL 不冲突）
    UNIQUE KEY uk_member_submit_token (member_id, submit_token),
    KEY idx_member_created (member_id, created_at),
    KEY idx_status_created (status, created_at)
);

CREATE TABLE order_item (
    id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    sku_code VARCHAR(64),
    specifications_json TEXT,
    main_image_url VARCHAR(512),
    unit_price_fen BIGINT NOT NULL,
    quantity INT NOT NULL,
    subtotal_fen BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
);

CREATE TABLE order_status_history (
    id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    operation VARCHAR(24) NOT NULL,
    operator VARCHAR(64) NOT NULL,
    reason VARCHAR(255),
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_occurred (order_id, occurred_at)
);
