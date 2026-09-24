-- CHG-0025 M7 STORY-009-04-01：延迟取消任务管理权限 + 菜单

INSERT INTO auth_permission(code,name,type,status,api_pattern,http_method) VALUES
('order-delay:list','延迟取消任务查询','API','ENABLED','/api/admin/order-delay/tasks','GET'),
('order-delay:cancel','延迟任务人工取消','API','ENABLED','/api/admin/order-delay/tasks/*/cancel','POST')
ON DUPLICATE KEY UPDATE name=VALUES(name),type=VALUES(type),status='ENABLED',
    api_pattern=VALUES(api_pattern),http_method=VALUES(http_method);

-- 延迟取消任务页（分布式增强目录由 V13 建立，此处仅做存在性兜底）
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT NULL,'分布式增强','DIRECTORY','/distributed',NULL,NULL,80,TRUE,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/distributed' AND type='DIRECTORY');

INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'延迟取消任务','PAGE','/distributed/delay','DelayTaskList','order-delay:list',10,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/distributed' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/distributed/delay');

-- 超管角色补齐新权限
INSERT IGNORE INTO auth_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM auth_role r CROSS JOIN auth_permission p
WHERE r.code='SUPER_ADMIN' AND p.code IN ('order-delay:list','order-delay:cancel');

-- 页面授权（目录授权已在 V13 完成，此处兜底）
INSERT IGNORE INTO auth_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM auth_role r CROSS JOIN auth_menu m
WHERE r.code='SUPER_ADMIN' AND m.path IN ('/distributed','/distributed/delay');
