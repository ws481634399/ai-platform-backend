# DU Task Spec — DU-BE-702

> 权威 DU 划分：外部 story-design.md §5；TC 定义在外部 Story test-design.md。

## 0. 元信息

- DU id: DU-BE-702
- Change ID: CHG-0023
- Feature Path: 平台验收补强/M5 验收缺口补强/管理台与素材/RBAC 管理台（管理员-角色-菜单权限）
- 权威来源: story-design.md §5 / DU-BE-702

## 任务清单

- [ ] 任务 1 — 新增 mall-identity V11__rbac_admin_menus.sql：建"权限管理"目录（/security, DIRECTORY）与三个子菜单（管理员管理 /security/admins→admin:read；角色管理 /security/roles→role:read；菜单权限 /security/menus→menu:read；component_key 与 mall-admin component-registry 注册键严格一致），全部 NOT EXISTS 幂等（verifies: TC-701-11）
- [ ] 任务 2 — V11 内将四个菜单（目录+三子菜单）以 INSERT IGNORE ... SELECT role code='SUPER_ADMIN' 方式授予超管角色（对齐 V3/V9 既有写法）（verifies: TC-701-11）
- [ ] 任务 3 — 迁移测试/既有 identity 测试回归：H2 迁移成功、重复执行幂等、bootstrap 超管菜单含四项；不新增权限码（verifies: TC-701-11, TC-701-12）

## Acceptance Criteria

- [ ] AC-005 — 给定全新库当迁移到 V11 则 auth_menu 出现权限管理目录及三菜单、auth_role_menu 出现超管授权；给定已迁移库重跑则不重复插入；超管 GET /api/admin/session/bootstrap 菜单含四节点；mall-identity 既有全部测试保持全绿

## 执行顺序（Execution Order）

1. 任务 1 → 2 → 3

## 并行度（Parallelization）

无（同一 SQL 文件内顺序语句）。

## Verification

- Unit: mall-identity 既有 Flyway/迁移测试与 AdminSession bootstrap 相关测试全量回归（mvn -pl mall-services/mall-identity test，加 -Dsurefire.failIfNoSpecifiedTests=false）
- Integration: N/A（bootstrap 真实登录串联属 C 类联调）
- API: bootstrap 出参菜单断言（若现有测试切片覆盖 bootstrap 则追加四节点断言）
- Migration: H2 前向迁移 + 幂等重跑；SQL 双方言（INSERT ... WHERE NOT EXISTS / INSERT IGNORE）与 V3/V9 风格一致
- Error Case: N/A（纯 DML 种子）
