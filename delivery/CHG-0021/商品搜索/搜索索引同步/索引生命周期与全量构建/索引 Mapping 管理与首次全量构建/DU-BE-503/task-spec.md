# DU Task Spec — DU-BE-503

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-503
- Change ID: CHG-0021
- Feature Path: 商品搜索/搜索索引同步/索引生命周期与全量构建/索引 Mapping 管理与首次全量构建
- 权威来源: story-design.md §5 / DU-BE-503

## 任务清单

- [ ] 任务 1 — V1 Flyway 两表（rebuild_task/failure_record 列与索引）（verifies: TC-007）
- [ ] 任务 2 — mapping/settings 资源（价格 long、docId keyword、mainImage no-index、date）（verifies: TC-002）
- [ ] 任务 3 — SearchIndexLifecycleManager 幂等建索引/别名（含 ES 宕机不阻启动）（verifies: TC-001, TC-008）
- [ ] 任务 4 — product 投影 Mapper（EXISTS 口径/价格极值）与分页、单条端点 SERVICE 鉴权（verifies: TC-003, TC-004, TC-009）
- [ ] 任务 5 — ProductProjectionClient（mall-search Feign）（verifies: TC-005）
- [ ] 任务 6 — SearchIndexBuildService 分批 Bulk 全量构建与批次失败 FAILED（verifies: TC-005, TC-006）

## Acceptance Criteria

- [ ] AC-001 — 全新 ES 启动自动建 v1+别名；重启不报错不改 Mapping。
- [ ] AC-002 — Mapping 代码可评审，字段类型符合冻结设计。
- [ ] AC-003 — 投影端点无令牌拒绝、网关 404；持令牌分页正确含 total。
- [ ] AC-004 — 投影排除下架/无启用 SKU；价格极值与启用 SKU 一致。
- [ ] AC-005 — 全量后 ES count=投影总数；抽样字段正确。
- [ ] AC-006 — 1001 条分批成功；中途失败 FAILED 且错误可定位。
- [ ] AC-007 — V1 在 mall_search 建两表。

## 执行顺序（Execution Order）

1. 任务 1/2 → 3 → 4 → 5 → 6。

## 并行度（Parallelization）

任务 1/2 与任务 4 可并行。

## Verification

- Unit: 投影 SQL Mapper 测试（口径/极值）；ensureIndex 幂等单测可 mock client。
- Integration: ES Testcontainers + MySQL testcontainers：建索引/别名幂等、全量 count、1001 条分批、失败 FAILED、ES 宕机启动。
- API: MockMvc SERVICE 401/403/200 与 data=null 语义。
- Migration: V1 前向迁移断言列/索引。
- Error Case: ES 宕机启动、投影 5xx（构建失败记录）、批次中途失败。
