-- CHG-0021 M5 搜索索引：search:index:* 权限点 + 搜索索引管理菜单

INSERT INTO auth_permission(code,name,type,status,api_pattern,http_method) VALUES
('search:index:list','索引状态查询','API','ENABLED','/api/admin/search/index/**','GET'),
('search:index:rebuild','索引重建/失败重试','API','ENABLED','/api/admin/search/index/**',NULL)
ON DUPLICATE KEY UPDATE name=VALUES(name),type=VALUES(type),status='ENABLED',
    api_pattern=VALUES(api_pattern),http_method=VALUES(http_method);

-- 搜索索引顶级目录
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT NULL,'搜索索引','DIRECTORY','/search',NULL,NULL,70,TRUE,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/search' AND type='DIRECTORY');

-- 索引管理页（重建/一致性/失败记录）
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'索引管理','PAGE','/search/index','SearchIndex','search:index:list',0,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/search' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/search/index');

-- 超管角色补齐新权限
INSERT IGNORE INTO auth_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM auth_role r CROSS JOIN auth_permission p
WHERE r.code='SUPER_ADMIN' AND p.code IN (
  'search:index:list','search:index:rebuild'
);

-- 目录与页面必须同时授权（bootstrap 菜单树仅装配已授权目录）
INSERT IGNORE INTO auth_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM auth_role r CROSS JOIN auth_menu m
WHERE r.code='SUPER_ADMIN' AND m.path IN ('/search','/search/index');
