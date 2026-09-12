# DU Task — DU-BE-201

> Workspace 权威来源：delivery/changes/CHG-0008/身份与权限/后台 RBAC 权限体系/权限主体管理/维护管理员生命周期/tasks.md

### DU-BE-201: 维护管理员生命周期

- Repository: repo-1
- Goal: DES-001：实现本 Story 在 repo-1 的职责
- Scope: [S1] 仅实现 STORY-001-02-01-01 的 AC-001, AC-002, AC-003，不扩展相邻 Story。
- Design References: story-design.md §1–§6；change-design.md §2、§4、§5
- Dependencies: 无
- Acceptance Criteria: AC-001, AC-002, AC-003
- Execution Order: 1
- Parallelization: 可与无依赖 DU 并行
- verifies: TC-001, TC-002, TC-003
- Implementation Sketch: 依次固化领域/状态模型、持久化或前端状态边界、应用编排和入口适配；未知状态与越权默认拒绝，敏感信息不进入响应和日志。
- Pseudocode: VALIDATE input and subject; LOAD current state/version; REJECT invalid status, permission or replay; EXECUTE 维护管理员生命周期; PERSIST atomically; RETURN sanitized result.
- Verification: 逐项执行下表 TC 红灯→最小实现→绿灯；随后运行模块测试、静态检查和契约回归。

#### 可执行任务

| Task | 文件/模块 | 具体改动 | verifies | depends on |
|---|---|---|---|---|
| TASK-001 | mall-identity/.../admin/AdminUserService.java | 实现管理员创建、查询、启停和改密 | TC-001, TC-002, TC-003 | — |
| TASK-002 | mall-identity/.../api/AdminUserController.java | 实现分页 CRUD 与 400/404/409 | TC-001, TC-002, TC-003 | TASK-001 |
| TASK-003 | mall-identity/.../persistence/AdminCredentialMapper.java | 增加状态和版本更新 SQL | TC-001, TC-002, TC-003 | TASK-002 |
| TASK-004 | mall-identity/.../AdminUserServiceTest.java | 覆盖唯一性、状态和版本失效 | TC-001, TC-002, TC-003 | TASK-003 |
