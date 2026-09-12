# DU Task — DU-BE-101

> Workspace 权威来源：delivery/changes/CHG-0007/身份与权限/统一身份认证体系/管理员凭证认证/安全存储与校验管理员凭证/tasks.md

### DU-BE-101: 安全存储与校验管理员凭证

- Repository: repo-1
- Goal: DES-001：实现本 Story 在 repo-1 的职责
- Scope: [S1] 仅实现 STORY-001-01-01-01 的 AC-001, AC-002, AC-003，不扩展相邻 Story。
- Design References: story-design.md §1–§6；change-design.md §2、§4、§5
- Dependencies: 无
- Acceptance Criteria: AC-001, AC-002, AC-003
- Execution Order: 1
- Parallelization: 可与无依赖 DU 并行
- verifies: TC-001, TC-002, TC-003
- Implementation Sketch: 依次固化领域/状态模型、持久化或前端状态边界、应用编排和入口适配；未知状态与越权默认拒绝，敏感信息不进入响应和日志。
- Pseudocode: VALIDATE input and subject; LOAD current state/version; REJECT invalid status, permission or replay; EXECUTE 安全存储与校验管理员凭证; PERSIST atomically; RETURN sanitized result.
- Verification: 逐项执行下表 TC 红灯→最小实现→绿灯；随后运行模块测试、静态检查和契约回归。

#### 可执行任务

| Task | 文件/模块 | 具体改动 | verifies | depends on |
|---|---|---|---|---|
| TASK-001 | mall-identity/.../auth/AdminCredential.java | 定义凭证、用户名规范化、状态与敏感字段不变量 | TC-001, TC-002, TC-003 | — |
| TASK-002 | mall-identity/.../persistence/AdminCredentialMapper.java + V1 migration | 参数化持久化并建立用户名唯一约束 | TC-001, TC-002, TC-003 | TASK-001 |
| TASK-003 | mall-identity/.../auth/AdminAuthenticationService.java | 使用 BCrypt 校验密码和账号状态 | TC-001, TC-002, TC-003 | TASK-002 |
| TASK-004 | mall-identity/.../auth/*Test.java | 覆盖强哈希、禁用账号和脱敏 | TC-001, TC-002, TC-003 | TASK-003 |
