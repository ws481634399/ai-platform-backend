# DU Task — DU-BE-204

> Workspace 权威来源：delivery/changes/CHG-0008/stories/STORY-001-02-02-01/tasks.md

### DU-BE-204: 维护后台菜单树

- Repository: repo-1
- Goal: DES-001：实现本 Story 在 repo-1 的职责
- Scope: [S1] 仅实现 STORY-001-02-02-01 的 AC-001, AC-002, AC-003，不扩展相邻 Story。
- Design References: story-design.md §1–§6；change-design.md §2、§4、§5
- Dependencies: 无
- Acceptance Criteria: AC-001, AC-002, AC-003
- Execution Order: 1
- Parallelization: 可与无依赖 DU 并行
- verifies: TC-001, TC-002, TC-003
- Implementation Sketch: 依次固化领域/状态模型、持久化或前端状态边界、应用编排和入口适配；未知状态与越权默认拒绝，敏感信息不进入响应和日志。
- Pseudocode: VALIDATE input and subject; LOAD current state/version; REJECT invalid status, permission or replay; EXECUTE 维护后台菜单树; PERSIST atomically; RETURN sanitized result.
- Verification: 逐项执行下表 TC 红灯→最小实现→绿灯；随后运行模块测试、静态检查和契约回归。

#### 可执行任务

| Task | 文件/模块 | 具体改动 | verifies | depends on |
|---|---|---|---|---|
| TASK-001 | mall-identity/.../rbac/Menu.java | 定义树节点、路由 path、componentKey | TC-001, TC-002, TC-003 | — |
| TASK-002 | mall-identity/.../rbac/MenuService.java | 实现树 CRUD、循环检测和排序 | TC-001, TC-002, TC-003 | TASK-001 |
| TASK-003 | mall-identity/.../api/MenuController.java | 暴露菜单树管理 API | TC-001, TC-002, TC-003 | TASK-002 |
| TASK-004 | mall-identity/.../MenuServiceTest.java | 覆盖循环、孤儿、隐藏和排序 | TC-001, TC-002, TC-003 | TASK-003 |
