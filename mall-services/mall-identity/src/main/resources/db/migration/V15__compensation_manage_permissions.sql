-- CHG-0025 M7 STORY-009-05-01：补偿任务管理三权限（查询/重试/人工完成）

INSERT INTO auth_permission(code,name,type,status,api_pattern,http_method) VALUES
('system:compensation:list','补偿任务查询','API','ENABLED','/api/admin/compensations','GET'),
('system:compensation:retry','补偿任务手动重试','API','ENABLED','/api/admin/compensations/*/retry','POST'),
('system:compensation:complete','补偿任务手动完成','API','ENABLED','/api/admin/compensations/*/complete','POST')
ON DUPLICATE KEY UPDATE name=VALUES(name),type=VALUES(type),status='ENABLED',
    api_pattern=VALUES(api_pattern),http_method=VALUES(http_method);

-- 既有补偿台页面（V8 建立，component_key/path 不变）切换到新查询权限码
UPDATE auth_menu
SET permission_code='system:compensation:list'
WHERE path='/orders/compensations' AND type='PAGE';

-- 超管角色补齐三权限（旧 order:compensation 保留不删）
INSERT IGNORE INTO auth_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM auth_role r CROSS JOIN auth_permission p
WHERE r.code='SUPER_ADMIN'
  AND p.code IN ('system:compensation:list','system:compensation:retry','system:compensation:complete');
