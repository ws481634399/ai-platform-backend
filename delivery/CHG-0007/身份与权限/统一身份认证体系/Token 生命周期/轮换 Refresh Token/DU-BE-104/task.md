# DU Task — DU-BE-104

> Workspace 权威来源：delivery/changes/CHG-0007/身份与权限/统一身份认证体系/Token 生命周期/轮换 Refresh Token/tasks.md

### DU-BE-104: 轮换 Refresh Token

- Repository: repo-1
- Goal: DES-001：实现本 Story 在 repo-1 的职责
- Scope: [S1] 仅实现 STORY-001-01-02-02 的 AC-001, AC-002, AC-003，不扩展相邻 Story。
- Design References: story-design.md §1–§6；change-design.md §2、§4、§5
- Dependencies: 无
- Acceptance Criteria: AC-001, AC-002, AC-003
- Execution Order: 1
- Parallelization: 可与无依赖 DU 并行
- verifies: TC-001, TC-002, TC-003
- Implementation Sketch: 依次固化领域/状态模型、持久化或前端状态边界、应用编排和入口适配；未知状态与越权默认拒绝，敏感信息不进入响应和日志。
- Pseudocode: VALIDATE input and subject; LOAD current state/version; REJECT invalid status, permission or replay; EXECUTE 轮换 Refresh Token; PERSIST atomically; RETURN sanitized result.
- Verification: 逐项执行下表 TC 红灯→最小实现→绿灯；随后运行模块测试、静态检查和契约回归。

#### 可执行任务

| Task | 文件/模块 | 具体改动 | verifies | depends on |
|---|---|---|---|---|
| TASK-001 | mall-identity/.../token/RefreshToken*.java | 实现随机 Token、SHA-256 摘要和持久化 | TC-001, TC-002, TC-003 | — |
| TASK-002 | mall-identity/.../token/RefreshTokenService.java | 实现一次性轮换与 family 重放撤销 | TC-001, TC-002, TC-003 | TASK-001 |
| TASK-003 | mall-identity/.../api/AdminAuthController.java | 实现 POST /refresh | TC-001, TC-002, TC-003 | TASK-002 |
| TASK-004 | mall-identity/.../token/RefreshTokenServiceTest.java | 覆盖轮换、过期、重放和并发 | TC-001, TC-002, TC-003 | TASK-003 |
