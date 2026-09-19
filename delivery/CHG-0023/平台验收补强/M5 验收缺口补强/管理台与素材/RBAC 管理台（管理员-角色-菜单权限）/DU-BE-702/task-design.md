# DU Task Design — DU-BE-702

## 1. Goal

为 RBAC 管理台补齐动态菜单入口：mall-identity V11 菜单种子（目录+三子菜单）与超管授权。经核对 V2~V10：权限码 V2 已播种（admin:*/role:*/menu:*），但无任何安全管理菜单行，故本 DU 确定触发。不新增权限码、无表结构变更。

## 2. Repository

repo-1（ai-platform-backend/mall-services/mall-identity）。

## 3. Scope

- Target：src/main/resources/db/migration/V11__rbac_admin_menus.sql（新增）。
- Data Objects：auth_menu（新增 4 行：1 DIRECTORY + 3 MENU）、auth_role_menu（超管 4 行授权）。
- 列结构沿用 V3/V9：auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status)；目录 permission_code 可空，子菜单分别绑 admin:read/role:read/menu:read。
- component_key：security.admins / security.roles / security.menus（与 DU-FE-701 component-registry 注册键约定一致，实施时双向核对）。

## 4. Design References

- requirement-design.md §2.1 条件 V11 说明、§4 数据契约（仅 DML）；
- story-design.md §1 repo-1 段（触发判定与 SQL 要求）；
- 先例迁移：V3__add_product_category_brand_permissions.sql、V9__add_search_index_permissions.sql（NOT EXISTS 插菜单 + INSERT IGNORE 超管授权）。

## 5. Dependencies

无（与 DU-FE-701 并行；component_key 字符串约定在 story-design 已冻结）。

## 6. Implementation Sketch

- 先插目录：INSERT INTO auth_menu(...) SELECT 常量 WHERE NOT EXISTS(path='/security' AND type='DIRECTORY')。
- 再插三子菜单：parent_id 子查询取目录 id；WHERE NOT EXISTS 按 path 判重；type='MENU'，visible=1，status='ENABLED'，sort_order 取 90 段避免与业务菜单冲突。
- 授权：INSERT IGNORE INTO auth_role_menu(role_id,menu_id) SELECT r.id,m.id FROM auth_role r CROSS JOIN auth_menu m WHERE r.code='SUPER_ADMIN' AND m.path IN ('/security','/security/admins','/security/roles','/security/menus')。
- 无 Java 代码变更；bootstrap 查询自动携带新菜单（既有菜单加载逻辑不过滤该前缀，实施时核对 mapper SQL）。
- 错误处理：纯 DDL/DML 幂等；失败即迁移失败阻断启动（期望行为）。

## 7. Pseudocode

N/A + 理由：未命中 complexity-trigger（声明式种子 SQL，无分支/算法；模式与 V3/V9 完全同构，§6 已给全部语句形状）。
