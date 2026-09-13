-- CHG-0010 商品与库存：分类与品牌主数据
-- 约定：一级分类 parent_id = 0（非 NULL），以便 (parent_id,name) 唯一索引对一级分类同样生效；
-- status 取值 ENABLED / DISABLED，由应用层枚举约束。

CREATE TABLE product_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(32) NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    level TINYINT NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_product_category_parent_name UNIQUE (parent_id, name),
    INDEX idx_product_category_parent_sort (parent_id, sort, id)
);

CREATE TABLE product_brand (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    logo VARCHAR(512) NULL,
    description VARCHAR(255) NULL,
    sort INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_product_brand_name UNIQUE (name),
    INDEX idx_product_brand_sort (sort, id)
);
