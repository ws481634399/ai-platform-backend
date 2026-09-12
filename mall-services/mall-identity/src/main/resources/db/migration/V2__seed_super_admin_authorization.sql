INSERT INTO auth_role(code,name,description,status,built_in)
VALUES('SUPER_ADMIN','超级管理员','系统内置不可删除角色','ENABLED',TRUE)
ON DUPLICATE KEY UPDATE name=VALUES(name),built_in=TRUE;

INSERT INTO auth_permission(code,name,type,status) VALUES
('admin:create','创建管理员','API','ENABLED'),('admin:read','查询管理员','API','ENABLED'),
('admin:update','修改管理员','API','ENABLED'),('role:create','创建角色','API','ENABLED'),
('role:read','查询角色','API','ENABLED'),('role:update','修改角色','API','ENABLED'),
('role:delete','删除角色','API','ENABLED'),('permission:create','创建权限','API','ENABLED'),
('permission:read','查询权限','API','ENABLED'),('permission:update','修改权限','API','ENABLED'),
('permission:delete','删除权限','API','ENABLED'),('menu:create','创建菜单','API','ENABLED'),
('menu:read','查询菜单','API','ENABLED'),('menu:update','修改菜单','API','ENABLED'),
('menu:delete','删除菜单','API','ENABLED'),('admin-role:assign','分配管理员角色','API','ENABLED'),
('role-permission:assign','分配角色权限','API','ENABLED')
ON DUPLICATE KEY UPDATE name=VALUES(name),status='ENABLED';

INSERT INTO auth_menu(parent_id,name,type,path,component_key,sort_order,visible,status)
SELECT NULL,'工作台','PAGE','/dashboard','Workbench',0,TRUE,'ENABLED'
WHERE NOT EXISTS(SELECT 1 FROM auth_menu WHERE path='/dashboard');

INSERT IGNORE INTO auth_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM auth_role r CROSS JOIN auth_permission p WHERE r.code='SUPER_ADMIN';

INSERT IGNORE INTO auth_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM auth_role r CROSS JOIN auth_menu m WHERE r.code='SUPER_ADMIN';
