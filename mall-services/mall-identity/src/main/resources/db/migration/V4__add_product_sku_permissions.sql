-- CHG-0011 商品与 SKU：product:product:* 与 product:sku:* 权限点 + 商品列表菜单
-- 幂等：按 code/path 去重，授权按 role code 子查询定位。

INSERT INTO auth_permission(code,name,type,status,api_pattern,http_method) VALUES
('product:product:list','查询商品','API','ENABLED','/api/admin/products/**','GET'),
('product:product:detail','商品详情','API','ENABLED','/api/admin/products/**','GET'),
('product:product:create','创建商品','API','ENABLED','/api/admin/products','POST'),
('product:product:update','修改商品','API','ENABLED','/api/admin/products/**','PUT'),
('product:product:disable','启停商品','API','ENABLED','/api/admin/products/**','PUT'),
('product:sku:create','新增SKU','API','ENABLED','/api/admin/products/**/skus','POST'),
('product:sku:update','修改SKU','API','ENABLED','/api/admin/products/**/skus/**','PUT'),
('product:sku:disable','启停SKU','API','ENABLED','/api/admin/products/**/skus/**','PUT')
ON DUPLICATE KEY UPDATE name=VALUES(name),type=VALUES(type),status='ENABLED',
    api_pattern=VALUES(api_pattern),http_method=VALUES(http_method);

-- 商品列表页（挂到商品管理目录下）
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'商品列表','PAGE','/product/products','ProductList','product:product:list',0,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/product' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/product/products');

-- 超管角色补齐新权限与菜单授权
INSERT IGNORE INTO auth_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM auth_role r CROSS JOIN auth_permission p
WHERE r.code='SUPER_ADMIN' AND p.code IN (
  'product:product:list','product:product:detail','product:product:create','product:product:update','product:product:disable',
  'product:sku:create','product:sku:update','product:sku:disable'
);

INSERT IGNORE INTO auth_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM auth_role r CROSS JOIN auth_menu m
WHERE r.code='SUPER_ADMIN' AND m.path='/product/products';
