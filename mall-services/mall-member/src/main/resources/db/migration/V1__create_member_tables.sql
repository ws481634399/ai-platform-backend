-- CHG-0016 商城会员表（STORY-003-01-01-01 / DU-BE-601）
-- 本 Story 仅 member_profile；收货地址表由地址 Story（DU-BE-604）追加 V2。

CREATE TABLE member_profile (
    member_id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    nickname VARCHAR(32) NOT NULL,
    avatar_url VARCHAR(512) NULL,
    gender VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    phone VARCHAR(20) NULL,
    email VARCHAR(128) NULL,
    initialized_event_id CHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_profile_event UNIQUE (initialized_event_id)
);
