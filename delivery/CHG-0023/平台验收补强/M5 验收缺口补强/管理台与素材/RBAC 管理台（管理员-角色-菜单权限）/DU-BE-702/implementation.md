# DU Implementation — DU-BE-702

## 变更内容

mall-identity 菜单种子与 RBAC 读模型回显补齐：

1. V11__rbac_admin_menus.sql（新增）：权限管理目录 /security（DIRECTORY，sort 90）+ 三 PAGE 菜单（/security/admins→SecurityAdmins/admin:read、/security/roles→SecurityRoles/role:read、/security/menus→SecurityMenus/menu:read），NOT EXISTS 幂等；超管角色四菜单 auth_role_menu 授权（INSERT IGNORE ... SELECT SUPER_ADMIN，仿 V3/V9）。不新增权限码、无表结构变更。
2. 读模型出参补齐（实施 Deviation，见 DEV-1）：
   - AdminUserQuery.Summary 增加 roleIds；AdminUserMapper 新增 findRoleRefs 页内批量 IN 查询（AdminRoleRef 记录）；MyBatisAdminUserQuery.page 按 adminId 分组装配，无角色给空列表（不 N+1、不出 null）。
   - RbacAdministrationApplicationService.RoleView 增加 permissionIds/menuIds（Role 聚合 findAll 已加载授权，零额外 SQL）。
3. 测试：
   - V11RbacAdminMenusMigrationTest（新增，2 用例）：四菜单/字段断言 + 超管四授权；
   - IdentityRepositoryIntegrationTest：分页行携带 roleIds 断言 + 无角色空列表用例（共 3 用例）；
   - RbacAdministrationApplicationServiceTest：listRoles 视图携带授权 id 用例（共 4 用例）；
   - M1AcceptanceScenariosTest：Summary 构造签名同步。

门禁：mall-identity 全量 mvn test 83 用例全绿（基线 79 + 净增 4；clean test 验证，此前增量目录脏数据导致的 2 例误报已排除）。

## Commits

见同 Story 根 implementation.md §2（本地提交，未 push）。

## Deviations

### DEV-1
- 原 DU 建议: 本 DU 仅 V11 菜单种子，"不新增后端能力"。
- 实际实现: 另在既有出参视图补 roleIds（管理员分页）与 permissionIds/menuIds（角色列表），无新端点/新权限码/无表结构变更。
- 原因: 前端实施核对发现 GET /admins、GET /roles 出参不含关联 id，管理台"分配角色/授权"只能盲操作全量覆盖，不满足 A1 可演示性要求；Role 聚合 findAll 本已加载授权、管理员关联仅多一次页内批量查询，补字段成本与风险均低。
- 影响评估: 纯追加响应字段，向后兼容；既有测试仅一处 record 构造签名同步；新增 4 用例出证。已同步 Story requirement/story 设计口径（implementation 记录），后续 Story review 核对。

## 自检

- 任务 1~3 完成；AC-005 后端部分满足（迁移幂等、bootstrap 超管菜单可得——既有 bootstrap 装配逻辑按授权菜单树工作，V11 授权齐全）。
- SQL 风格与 V3/V9 一致（H2/MySQL 双方言已由集成测试验证 H2）。
