-- CHG-0022 M5 系统配置：system:* 权限点 + 功能开关/系统参数/变更历史菜单

INSERT INTO auth_permission(code,name,type,status,api_pattern,http_method) VALUES
('system:feature:list','功能开关查询','API','ENABLED','/api/admin/feature-configs/**','GET'),
('system:feature:update','功能开关变更','API','ENABLED','/api/admin/feature-configs/**',NULL),
('system:parameter:list','系统参数查询','API','ENABLED','/api/admin/system-parameters/**','GET'),
('system:parameter:update','系统参数变更','API','ENABLED','/api/admin/system-parameters/**',NULL),
('system:config-history:list','变更历史查询','API','ENABLED','/api/admin/config-history/**','GET')
ON DUPLICATE KEY UPDATE name=VALUES(name),type=VALUES(type),status='ENABLED',
    api_pattern=VALUES(api_pattern),http_method=VALUES(http_method);

-- 系统配置顶级目录
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT NULL,'系统配置','DIRECTORY','/system',NULL,NULL,80,TRUE,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/system' AND type='DIRECTORY');

-- 功能开关页
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'功能开关','PAGE','/system/features','FeatureConfigs','system:feature:list',0,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/system' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/system/features');

-- 系统参数页
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'系统参数','PAGE','/system/parameters','SystemParameters','system:parameter:list',10,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/system' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/system/parameters');

-- 变更历史页
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'变更历史','PAGE','/system/config-history','ConfigHistory','system:config-history:list',20,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/system' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/system/config-history');

-- 超管角色补齐新权限
INSERT IGNORE INTO auth_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM auth_role r CROSS JOIN auth_permission p
WHERE r.code='SUPER_ADMIN' AND p.code IN (
  'system:feature:list','system:feature:update',
  'system:parameter:list','system:parameter:update',
  'system:config-history:list'
);

-- 目录与页面必须同时授权（bootstrap 菜单树仅装配已授权目录）
INSERT IGNORE INTO auth_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM auth_role r CROSS JOIN auth_menu m
WHERE r.code='SUPER_ADMIN'
  AND m.path IN ('/system','/system/features','/system/parameters','/system/config-history');
