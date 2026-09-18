# DU Task Spec — DU-BE-504

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-504
- Change ID: CHG-0021
- Feature Path: 商品搜索/搜索索引同步/索引生命周期与全量构建/手工重建与索引一致性检查
- 权威来源: story-design.md §5 / DU-BE-504

## 任务清单

- [ ] 任务 1 — RebuildService：RUNNING 闸门 409/临时索引/全量构建/原子切别名/删旧/状态回写（verifies: TC-001, TC-002, TC-003）
- [ ] 任务 2 — 重建失败路径：FAILED+error_message，别名与旧索引不动（verifies: TC-002）
- [ ] 任务 3 — consistency-check：两计数+missing/extra 差集+200 截断+truncated（verifies: TC-004）
- [ ] 任务 4 — SearchIndexAdminController 三端点 + 权限注解（verifies: TC-005）
- [ ] 任务 5 — identity V9 权限码/菜单/超管种子 + 网关 admin 路由（verifies: TC-005, TC-006）
- [ ] 任务 6 — rebuild-tasks 列表端点（状态/进度/错误）（verifies: TC-002）

## Acceptance Criteria

- [ ] AC-001 — 构建中旧搜索不中断；完成后别名指新索引、新数据可搜、旧物理索引删除。
- [ ] AC-002 — RUNNING→SUCCESS（total/indexed 准确）；失败 FAILED 有错误信息且旧索引可查。
- [ ] AC-003 — RUNNING 中再触发 409 B0503 且带当前 taskId。
- [ ] AC-004 — 计数准确；删文档报 missing、孤儿 docId 报 extra；超 200 truncated。
- [ ] AC-005 — 无权限 403、菜单/按钮生效；网关未登录 admin 401。

## 执行顺序（Execution Order）

1. 任务 5（V9/网关，可先行）→ 1 → 2 → 3/6 → 4。

## 并行度（Parallelization）

任务 5 与 1–3 可并行。

## Verification

- Unit: RUNNING 闸门/截断逻辑单测（Clock 注入时间戳）。
- Integration: ES IT 完整重建（别名断言/旧索引 404/中途搜索可用）、差集构造（DELETE 3 条+插 2 孤儿）、mock 失败保留旧别名。
- API: MockMvc 200/409/403；网关 401 E2E。
- Migration: V9 权限菜单种子断言。
- Error Case: 并发 409、构建失败、比对时依赖故障 503 B0501。
