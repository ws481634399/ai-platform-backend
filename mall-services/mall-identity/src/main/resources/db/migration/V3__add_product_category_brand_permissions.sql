-- CHG-0010 商品与库存：分类/品牌 8 个权限点 + 3 个菜单（商品管理目录/分类页/品牌页）
-- 幂等：权限与菜单按 code/path 去重，授权按 role code 子查询定位，不硬编码角色 id。

INSERT INTO auth_permission(code,name,type,status,api_pattern,http_method) VALUES
('product:category:list','查询商品分类','API','ENABLED','/api/admin/categories/**','GET'),
('product:category:create','创建商品分类','API','ENABLED','/api/admin/categories','POST'),
('product:category:update','修改商品分类','API','ENABLED','/api/admin/categories/**','PUT'),
('product:category:disable','启停商品分类','API','ENABLED','/api/admin/categories/**','PUT'),
('product:brand:list','查询商品品牌','API','ENABLED','/api/admin/brands/**','GET'),
('product:brand:create','创建商品品牌','API','ENABLED','/api/admin/brands','POST'),
('product:brand:update','修改商品品牌','API','ENABLED','/api/admin/brands/**','PUT'),
('product:brand:disable','启停商品品牌','API','ENABLED','/api/admin/brands/**','PUT')
ON DUPLICATE KEY UPDATE name=VALUES(name),type=VALUES(type),status='ENABLED',
    api_pattern=VALUES(api_pattern),http_method=VALUES(http_method);

-- 商品管理顶级目录
INSERT INTO auth_menu(parent_id,name,type,path,component_key,sort_order,visible,status)
SELECT NULL,'商品管理','DIRECTORY','/product',NULL,20,TRUE,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/product' AND type='DIRECTORY');

-- 分类页/品牌页（parent_id 指向商品管理目录）
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'分类管理','PAGE','/product/categories','CategoryTree','product:category:list',1,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/product' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/product/categories');

INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'品牌管理','PAGE','/product/brands','BrandList','product:brand:list',2,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/product' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/product/brands');

-- 超管角色补齐新权限与新菜单授权
INSERT IGNORE INTO auth_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM auth_role r CROSS JOIN auth_permission p
WHERE r.code='SUPER_ADMIN' AND p.code LIKE 'product:%';

INSERT IGNORE INTO auth_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM auth_role r CROSS JOIN auth_menu m
WHERE r.code='SUPER_ADMIN' AND m.path IN ('/product','/product/categories','/product/brands');
