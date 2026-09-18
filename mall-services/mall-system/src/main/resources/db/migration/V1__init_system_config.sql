-- CHG-0022 M5 系统配置：功能开关 / 类型化系统参数 / 变更历史 三表 + 4 个内置键种子
-- 设计冻结：requirement-design.md §7 / story-design STORY-006-01-01-01 §1

CREATE TABLE feature_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    config_key VARCHAR(100) NOT NULL,
    feature_name VARCHAR(100) NOT NULL,
    config_group VARCHAR(50) NOT NULL DEFAULT 'default',
    enabled TINYINT NOT NULL DEFAULT 0,
    public_flag TINYINT NOT NULL DEFAULT 0,
    built_in TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    description VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feature_config_key (config_key)
);

CREATE TABLE system_parameter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    config_key VARCHAR(100) NOT NULL,
    parameter_name VARCHAR(100) NOT NULL,
    config_group VARCHAR(50) NOT NULL DEFAULT 'default',
    parameter_type VARCHAR(20) NOT NULL,
    config_value VARCHAR(1000) NOT NULL,
    default_value VARCHAR(1000) NOT NULL,
    min_value VARCHAR(64) NULL,
    max_value VARCHAR(64) NULL,
    effect_type VARCHAR(20) NOT NULL DEFAULT 'DYNAMIC',
    public_flag TINYINT NOT NULL DEFAULT 0,
    built_in TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    description VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_parameter_key (config_key)
);

CREATE TABLE system_config_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    config_type VARCHAR(16) NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    old_value VARCHAR(1000) NULL,
    new_value VARCHAR(1000) NULL,
    change_kind VARCHAR(16) NOT NULL,
    changed_by VARCHAR(64) NOT NULL,
    change_reason VARCHAR(500) NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_config_type_key (config_type, config_key, id)
);

-- 内置键种子（与 BuiltInConfigSeeder 双保险，以本 DML 为准）
INSERT INTO feature_config
    (config_key, feature_name, config_group, enabled, public_flag, built_in, version, description, created_at, updated_at)
VALUES
    ('search.enabled', '商品搜索', 'search', 1, 1, 1, 0, '商城商品搜索能力总开关', NOW(), NOW()),
    ('mall.guest-cart.enabled', '游客购物车', 'cart', 1, 1, 1, 0, '未登录游客的购物车写能力开关', NOW(), NOW());

INSERT INTO system_parameter
    (config_key, parameter_name, config_group, parameter_type, config_value, default_value,
     min_value, max_value, effect_type, public_flag, built_in, version, description, created_at, updated_at)
VALUES
    ('search.default-page-size', '搜索默认分页大小', 'search', 'INTEGER', '20', '20',
     '1', '100', 'DYNAMIC', 0, 1, 0, '商品搜索每页默认条数', NOW(), NOW()),
    ('cart.max-item-quantity', '购物车单品最大数量', 'cart', 'INTEGER', '99', '99',
     '1', '999', 'DYNAMIC', 0, 1, 0, '单个 SKU 在购物车中的数量上限', NOW(), NOW());
