-- CHG-0024 M6 AI 智能应用：ai:knowledge:* 权限点 + AI 智能应用目录/知识库管理菜单
-- 网关 /api/ai/admin/** 已强制 ROLE_ADMIN；此处权限码供管理台菜单/按钮级收口。

INSERT INTO auth_permission(code,name,type,status,api_pattern,http_method) VALUES
('ai:knowledge:list','知识库文档查询','API','ENABLED','/api/ai/admin/knowledge/documents','GET'),
('ai:knowledge:upload','知识库文档上传','API','ENABLED','/api/ai/admin/knowledge/documents',NULL),
('ai:knowledge:update','知识库文档启停','API','ENABLED','/api/ai/admin/knowledge/documents/*',NULL),
('ai:knowledge:delete','知识库文档删除','API','ENABLED','/api/ai/admin/knowledge/documents/*',NULL),
('ai:knowledge:rebuild','知识库索引重建','API','ENABLED','/api/ai/admin/knowledge/documents/*/rebuild',NULL)
ON DUPLICATE KEY UPDATE name=VALUES(name),type=VALUES(type),status='ENABLED',
    api_pattern=VALUES(api_pattern),http_method=VALUES(http_method);

-- AI 智能应用顶级目录
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT NULL,'AI 智能应用','DIRECTORY','/ai',NULL,NULL,70,TRUE,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/ai' AND type='DIRECTORY');

-- 知识库管理页
INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)
SELECT m.id,'知识库管理','PAGE','/ai/knowledge','AiKnowledge','ai:knowledge:list',0,TRUE,'ENABLED'
FROM auth_menu m WHERE m.path='/ai' AND m.type='DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM auth_menu WHERE path='/ai/knowledge');

-- 超管角色补齐新权限
INSERT IGNORE INTO auth_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM auth_role r CROSS JOIN auth_permission p
WHERE r.code='SUPER_ADMIN' AND p.code IN (
  'ai:knowledge:list','ai:knowledge:upload','ai:knowledge:update',
  'ai:knowledge:delete','ai:knowledge:rebuild'
);

-- 目录与页面必须同时授权（bootstrap 菜单树仅装配已授权目录）
INSERT IGNORE INTO auth_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM auth_role r CROSS JOIN auth_menu m
WHERE r.code='SUPER_ADMIN' AND m.path IN ('/ai','/ai/knowledge');
