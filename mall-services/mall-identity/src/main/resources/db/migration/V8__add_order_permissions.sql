-- CHG-0019 M4 订单交易闭环：order:* 权限点 + 订单管理菜单

INSERT INTO auth_permission(code,name,type,status,api_pattern,http_method) VALUES
('order:list','查询订单列表','API','ENABLED','/api/admin/orders/**','GET'),
('order:view','订单详情','API','ENABLED','/api/admin/orders/**','GET'),
('order:ship','订单发货','API','ENABLED','/api/admin/orders/**/ship','POST'),
('order:compensation','交易补偿台（查询/重试）','API','ENABLED','/api/admin/compensations/**',NULL)
ON DUPLICATE KEY UPDATE name=VALUES(name),type=VALUES(type),status='ENABLED',
    api_pattern=VALUES(api_pattern),http_method=VALUES(http_method);

-- 订单管理顶级目录
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT NULL,'订单管理','DIRECTORY','/orders',NULL,NULL,60,TRUE,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/orders' AND type='DIRECTORY');

-- 订单列表页
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'订单列表','PAGE','/orders/list','OrderList','order:list',0,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/orders' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/orders/list');

-- 交易补偿台
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'补偿任务','PAGE','/orders/compensations','CompensationList','order:compensation',1,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/orders' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/orders/compensations');

-- 超管角色补齐新权限
INSERT IGNORE INTO auth_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM auth_role r CROSS JOIN auth_permission p
WHERE r.code='SUPER_ADMIN' AND p.code IN (
  'order:list','order:view','order:ship','order:compensation'
);

-- 目录与页面必须同时授权（bootstrap 菜单树仅装配已授权目录）
INSERT IGNORE INTO auth_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM auth_role r CROSS JOIN auth_menu m
WHERE r.code='SUPER_ADMIN' AND m.path IN ('/orders','/orders/list','/orders/compensations');
