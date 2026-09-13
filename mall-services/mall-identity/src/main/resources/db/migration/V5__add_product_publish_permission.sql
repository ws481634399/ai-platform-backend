-- CHG-0012 商品发布与商城查询：product:product:publish 权限点
-- 幂等：按 code 去重，授权按 role code 子查询定位。

INSERT INTO auth_permission(code,name,type,status,api_pattern,http_method) VALUES
('product:product:publish','商品上下架','API','ENABLED','/api/admin/products/**','POST')
ON DUPLICATE KEY UPDATE name=VALUES(name),type=VALUES(type),status='ENABLED',
    api_pattern=VALUES(api_pattern),http_method=VALUES(http_method);

-- 超管角色补齐新权限
INSERT IGNORE INTO auth_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM auth_role r CROSS JOIN auth_permission p
WHERE r.code='SUPER_ADMIN' AND p.code='product:product:publish';
