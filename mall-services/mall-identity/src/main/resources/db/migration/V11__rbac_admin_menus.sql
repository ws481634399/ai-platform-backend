-- CHG-0023 A1 RBAC 管理台：权限管理目录 + 管理员/角色/菜单权限三页面菜单
-- 权限码（admin:*/role:*/menu:*）已在 V2 播种，本迁移仅补菜单与超管授权，不新增权限码。

-- 权限管理顶级目录
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT NULL,'权限管理','DIRECTORY','/security',NULL,NULL,90,TRUE,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/security' AND type='DIRECTORY');

-- 管理员管理页
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'管理员管理','PAGE','/security/admins','SecurityAdmins','admin:read',10,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/security' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/security/admins');

-- 角色管理页
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'角色管理','PAGE','/security/roles','SecurityRoles','role:read',20,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/security' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/security/roles');

-- 菜单权限查看页
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'菜单权限','PAGE','/security/menus','SecurityMenus','menu:read',30,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/security' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/security/menus');

-- 目录与页面必须同时授权（bootstrap 菜单树仅装配已授权目录）
INSERT IGNORE INTO auth_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM auth_role r CROSS JOIN auth_menu m
WHERE r.code='SUPER_ADMIN' AND m.path IN ('/security','/security/admins','/security/roles','/security/menus');
