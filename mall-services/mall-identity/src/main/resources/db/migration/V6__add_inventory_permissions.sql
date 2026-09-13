-- CHG-0013 库存核心能力：inventory:stock:* 与 inventory:log:list 权限点 + 库存管理菜单

INSERT INTO auth_permission(code,name,type,status,api_pattern,http_method) VALUES
('inventory:stock:list','查询库存','API','ENABLED','/api/admin/inventory/stocks/**','GET'),
('inventory:stock:detail','库存详情','API','ENABLED','/api/admin/inventory/stocks/**','GET'),
('inventory:stock:init','初始化库存','API','ENABLED','/api/admin/inventory/stocks/init','POST'),
('inventory:stock:adjust','调整库存','API','ENABLED','/api/admin/inventory/stocks/**/adjust','POST'),
('inventory:log:list','查询库存流水','API','ENABLED','/api/admin/inventory/logs/**','GET')
ON DUPLICATE KEY UPDATE name=VALUES(name),type=VALUES(type),status='ENABLED',
    api_pattern=VALUES(api_pattern),http_method=VALUES(http_method);

-- 库存管理目录（顶级目录 parent_id 必须为 NULL：auth_menu.fk_auth_menu_parent 不允许 0）
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT NULL,'库存管理','DIRECTORY','/inventory',NULL,NULL,50,TRUE,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/inventory' AND type='DIRECTORY');

-- 库存列表页
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'库存列表','PAGE','/inventory/stocks','InventoryList','inventory:stock:list',0,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/inventory' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/inventory/stocks');

-- 库存流水页
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'库存流水','PAGE','/inventory/logs','InventoryLog','inventory:log:list',1,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/inventory' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/inventory/logs');

-- 超管角色补齐新权限与菜单授权
INSERT IGNORE INTO auth_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM auth_role r CROSS JOIN auth_permission p
WHERE r.code='SUPER_ADMIN' AND p.code IN (
  'inventory:stock:list','inventory:stock:detail','inventory:stock:init','inventory:stock:adjust','inventory:log:list'
);

INSERT IGNORE INTO auth_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM auth_role r CROSS JOIN auth_menu m
WHERE r.code='SUPER_ADMIN' AND m.path IN ('/inventory/stocks','/inventory/logs');
