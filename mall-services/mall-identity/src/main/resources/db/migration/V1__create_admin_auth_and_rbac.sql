CREATE TABLE admin_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    auth_version BIGINT NOT NULL DEFAULT 1,
    permission_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_admin_user_username UNIQUE (username)
);

CREATE TABLE auth_refresh_token (
    digest CHAR(64) PRIMARY KEY,
    family_id CHAR(36) NOT NULL,
    admin_id BIGINT NOT NULL,
    auth_version BIGINT NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_refresh_family (family_id),
    INDEX idx_refresh_admin (admin_id),
    CONSTRAINT fk_refresh_admin FOREIGN KEY (admin_id) REFERENCES admin_user(id)
);

CREATE TABLE auth_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_auth_role_code UNIQUE (code)
);

CREATE TABLE auth_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    resource_description VARCHAR(255) NULL,
    type VARCHAR(16) NOT NULL DEFAULT 'BUTTON',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    api_pattern VARCHAR(255) NULL,
    http_method VARCHAR(16) NULL,
    CONSTRAINT uk_auth_permission_code UNIQUE (code)
);

CREATE TABLE auth_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT NULL,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(16) NOT NULL DEFAULT 'PAGE',
    path VARCHAR(255) NULL,
    component_key VARCHAR(128) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    permission_code VARCHAR(128) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_auth_menu_parent FOREIGN KEY (parent_id) REFERENCES auth_menu(id)
);

CREATE TABLE admin_user_role (
    admin_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (admin_id, role_id),
    CONSTRAINT fk_user_role_admin FOREIGN KEY (admin_id) REFERENCES admin_user(id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES auth_role(id)
);

CREATE TABLE auth_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES auth_role(id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES auth_permission(id)
);

CREATE TABLE auth_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id),
    CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id) REFERENCES auth_role(id),
    CONSTRAINT fk_role_menu_menu FOREIGN KEY (menu_id) REFERENCES auth_menu(id)
);

CREATE TABLE auth_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_admin_id BIGINT NOT NULL,
    action VARCHAR(128) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    result VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    trace_id VARCHAR(64) NULL,
    detail_json JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_auth_audit_actor_time (actor_admin_id, created_at)
);
