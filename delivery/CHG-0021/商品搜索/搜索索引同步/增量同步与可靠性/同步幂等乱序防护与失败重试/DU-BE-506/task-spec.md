# DU Task Spec — DU-BE-506

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-506
- Change ID: CHG-0021
- Feature Path: 商品搜索/搜索索引同步/增量同步与可靠性/同步幂等乱序防护与失败重试
- 权威来源: story-design.md §5 / DU-BE-506

## 任务清单

- [ ] 任务 1 — upsert external_gte 版本与 VersionConflict 消化（含旧 delete 晚到）（verifies: TC-001, TC-002, TC-003）
- [ ] 任务 2 — recordFailure 落表/去重/初始 30s（verifies: TC-004）
- [ ] 任务 3 — @Scheduled 扫描重放与退避序列、5 次 FAILED_DEAD（Clock 注入）（verifies: TC-004, TC-005）
- [ ] 任务 4 — 重放重拉投影：下架删除/重新上架 upsert（verifies: TC-007）
- [ ] 任务 5 — sync-failures 查询与人工 retry 端点（404 B0504）（verifies: TC-006）
- [ ] 任务 6 — IT 全绿：mvn -pl mall-services/mall-search -am test（verifies: TC-008）

## Acceptance Criteria

- [ ] AC-001 — 同事件两投：仅一份最新文档，无脏记录。
- [ ] AC-002 — v200 后到 v100 upsert：保持 v200，接口不报错。
- [ ] AC-003 — v200 后到 v100 delete：文档不删且有 WARN/INFO 日志。
- [ ] AC-004 — ES 停时 sync 落 PENDING；恢复后定时重试 SUCCESS 且可搜。
- [ ] AC-005 — 退避序列正确；第 5 次 FAILED_DEAD 不再被拾取。
- [ ] AC-006 — 人工重试 FAILED_DEAD 可激活并最终成功。
- [ ] AC-007 — 重试时已下架→删除 SUCCESS；重新上架→upsert 成功。
- [ ] AC-008 — Testcontainers/可控启停 IT 覆盖主路径，mvn test 全绿。

## 执行顺序（Execution Order）

1. 任务 1 → 2/3 → 4 → 5 → 6。

## 并行度（Parallelization）

任务 5 与 2–4 可并行。

## Verification

- Unit: 退避序列/状态流转（Clock 固定）、批次 LIMIT SQL、去重 upsert。
- Integration: 真实 ES external_gte 冲突；停 ES→落表→恢复→调度成功（直接调用扫描方法避免 30s 等待）。
- API: 人工 retry 200/404；列表筛选分页。
- Migration: N/A（复用 V1）。
- Error Case: 版本冲突、批次中单条失败隔离、投影下架/再上架。
