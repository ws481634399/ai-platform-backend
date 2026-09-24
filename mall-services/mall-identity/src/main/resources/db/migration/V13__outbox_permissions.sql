-- CHG-0025 M7 STORY-009-02-01：Outbox 事件管理权限 + 菜单

INSERT INTO auth_permission(code,name,type,status,api_pattern,http_method) VALUES
('system:outbox:list','Outbox 事件查询','API','ENABLED','/api/admin/outbox/events','GET'),
('system:outbox:retry','Outbox 事件重投','API','ENABLED','/api/admin/outbox/events/*/retry','POST')
ON DUPLICATE KEY UPDATE name=VALUES(name),type=VALUES(type),status='ENABLED',
    api_pattern=VALUES(api_pattern),http_method=VALUES(http_method);

-- 分布式增强顶级目录
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT NULL,'分布式增强','DIRECTORY','/distributed',NULL,NULL,80,TRUE,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/distributed' AND type='DIRECTORY');

-- Outbox 事件管理页
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'Outbox 事件','PAGE','/distributed/outbox','OutboxList','system:outbox:list',0,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/distributed' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/distributed/outbox');

-- 超管角色补齐新权限
INSERT IGNORE INTO auth_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM auth_role r CROSS JOIN auth_permission p
WHERE r.code='SUPER_ADMIN' AND p.code IN ('system:outbox:list','system:outbox:retry');

-- 目录与页面授权
INSERT IGNORE INTO auth_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM auth_role r CROSS JOIN auth_menu m
WHERE r.code='SUPER_ADMIN' AND m.path IN ('/distributed','/distributed/outbox');
