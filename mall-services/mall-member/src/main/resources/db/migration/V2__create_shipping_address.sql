-- CHG-0016 收货地址表（STORY-003-01-03-01 / DU-BE-604）
-- 默认唯一用 MySQL 8 无 partial unique index 的等效技巧：
-- 生成列 default_member_flag：默认地址写入 member_id，非默认写 NULL；UNIQUE 索引对多行 NULL 不限制，
-- 从而实现「每个会员至多一条 is_default=1」（requirement-design §1.5/§2.4）。
-- 注意：本 SQL 同时由 H2（MODE=MySQL）在测试库执行，故表达式取标准 CASE WHEN 且不写 STORED（详见列注释）。

CREATE TABLE shipping_address (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    receiver_name VARCHAR(32) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    province VARCHAR(64) NOT NULL,
    city VARCHAR(64) NOT NULL,
    district VARCHAR(64) NOT NULL,
    detail_address VARCHAR(128) NOT NULL,
    postal_code CHAR(6) NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    -- CASE WHEN 为标准 SQL（H2 MODE=MySQL 不接受 MySQL 方言 IF()，也不接受 STORED 关键字），
    -- MySQL 8/H2 两侧等价；省略 STORED 后 MySQL 默认 VIRTUAL——UNIQUE 索引对虚拟生成列同样物化键值，
    -- 每会员默认唯一的约束保证与 STORED 完全一致（该列不参与 SELECT/过滤，仅服务 uk）。
    default_member_flag BIGINT GENERATED ALWAYS AS (CASE WHEN is_default = 1 THEN member_id ELSE NULL END),
    INDEX idx_address_member (member_id, is_default, updated_at),
    CONSTRAINT uk_address_default UNIQUE (default_member_flag)
);
