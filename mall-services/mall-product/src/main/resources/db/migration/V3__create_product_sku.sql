-- CHG-0011 SKU 与规格：product_sku
-- specification_hash：排序后规格键值 SHA-256，保证同商品组合唯一。
-- specification_data 以 VARCHAR 存储 JSON 字符串，避免 MyBatis-Plus 对 JSON 列名/类型自动套用 JacksonTypeHandler。

CREATE TABLE product_sku (
    id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    sale_price BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    main_image_url VARCHAR(512) NULL,
    specification_data VARCHAR(1024) NOT NULL,
    specification_hash VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (sku_code, deleted),
    UNIQUE KEY uk_product_spec_hash (product_id, specification_hash, deleted),
    KEY idx_sku_product (product_id, status),
    KEY idx_sku_status (status),
    KEY idx_sku_price (sale_price)
);
