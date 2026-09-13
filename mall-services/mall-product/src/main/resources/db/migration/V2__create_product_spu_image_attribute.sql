-- CHG-0011 商品 SPU：product_spu / product_image / product_attribute
-- 价格区间 min_price/max_price 为查询投影（分），真实价格在 product_sku。

CREATE TABLE product_spu (
    id BIGINT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    subtitle VARCHAR(255) NULL,
    description LONGTEXT NULL,
    category_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    min_price BIGINT NULL,
    max_price BIGINT NULL,
    main_image_url VARCHAR(512) NULL,
    sales_count BIGINT NOT NULL DEFAULT 0,
    published_at DATETIME(3) NULL,
    unpublished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_code (product_code, deleted),
    KEY idx_product_status (status),
    KEY idx_product_category (category_id, status),
    KEY idx_product_brand (brand_id, status),
    KEY idx_product_created (created_at)
);

CREATE TABLE product_image (
    id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NULL,
    image_type VARCHAR(32) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    main_flag TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_product_image_product (product_id, image_type),
    KEY idx_product_image_sku (sku_id),
    KEY idx_product_image_sort (product_id, sort_order)
);

CREATE TABLE product_attribute (
    id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    attribute_name VARCHAR(128) NOT NULL,
    attribute_value VARCHAR(512) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_attribute (product_id, attribute_name),
    KEY idx_product_attribute_product (product_id, sort_order)
);
