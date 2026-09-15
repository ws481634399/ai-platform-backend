-- CHG-0016 商城会员账号（STORY-003-01-01-01 / DU-BE-601）
-- 与 ADMIN 链路物理隔离：独立账号表/刷新令牌表/事件 outbox，零侵入 admin_user 体系。
-- 注意：本库迁移已使用到 V6（V3~V6 为商品/库存权限种子），会员表必须从 V7 起编。

CREATE TABLE member_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    username_norm VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    auth_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_member_username_norm UNIQUE (username_norm)
);

CREATE TABLE member_refresh_token (
    digest CHAR(64) PRIMARY KEY,
    family_id CHAR(36) NOT NULL,
    member_id BIGINT NOT NULL,
    auth_version BIGINT NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_member_refresh_family (family_id),
    INDEX idx_member_refresh_member (member_id),
    CONSTRAINT fk_member_refresh_member FOREIGN KEY (member_id) REFERENCES member_user(id)
);

CREATE TABLE member_event_outbox (
    event_id CHAR(36) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    member_id BIGINT NOT NULL,
    -- 逻辑 JSON 载荷以 VARCHAR 存储（与 product_sku.specification_data 同先例）：
    -- 应用层 ObjectMapper 保证 JSON 合法性；规避 H2 MySQL 模式 JSON 列回读双编码问题，
    -- 同时避免 MyBatis-Plus 对 JSON 类型自动套用 TypeHandler。事件载荷 <1KB，2048 留足余量。
    payload_json VARCHAR(2048) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at TIMESTAMP(6) NULL,
    INDEX idx_member_outbox_status (status, created_at)
);
