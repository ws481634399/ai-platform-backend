# DU Task Spec — DU-BE-505

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-505
- Change ID: CHG-0021
- Feature Path: 商品搜索/搜索索引同步/增量同步与可靠性/商品变更增量同步
- 权威来源: story-design.md §5 / DU-BE-505

## 任务清单

- [ ] 任务 1 — ProductSearchChangedEvent 与 7 写方法发布点（op 映射含 changeStatus 分支）（verifies: TC-006）
- [ ] 任务 2 — AFTER_COMMIT 监听器（回滚不发送/提交后发送/全异常吞掉+ERROR）（verifies: TC-005, TC-006）
- [ ] 任务 3 — SearchSyncClient Feign（超时 1s/3s）与载荷组装（无启用 SKU→delete 语义）（verifies: TC-004, TC-009）
- [ ] 任务 4 — InternalSearchSyncController upsert/delete（SERVICE、受理 200、缺文档幂等）（verifies: TC-003, TC-007, TC-008）
- [ ] 任务 5 — 上架/改名/改价/下架跨服务联调（与 BE-506 合并后）（verifies: TC-001, TC-002）

## Acceptance Criteria

- [ ] AC-001 — 上架提交后 5s 内 ES 可检出。
- [ ] AC-002 — 改名/换分类品牌/换图/改价后文档字段与价格摘要刷新。
- [ ] AC-003 — 下架硬删除不可见；重复下架/删除不存在 id 成功。
- [ ] AC-004 — 新增/禁用 SKU 极值正确；无启用 SKU 在架商品不产生可搜文档。
- [ ] AC-005 — search 停摆时商品写操作仍成功不回滚，product 日志 WARN/ERROR。
- [ ] AC-006 — 事务回滚零同步调用。
- [ ] AC-007 — 内部端点需 SERVICE 身份，经网关 404。

## 执行顺序（Execution Order）

1. 任务 1/2 → 3 → 4 → 5（5 在 BE-506 完成后执行）。

## 并行度（Parallelization）

product 侧（1–3）与 search 侧（4）可并行。

## Verification

- Unit: @TransactionalEventListener 测试（回滚/提交矩阵、7 方法 op 断言）、监听器异常吞咽。
- Integration: 双模块 + ES Testcontainers 联调（改名/下架/改价轮询搜索）；停 mall-search 商品写 200。
- API: 内部端点 MockMvc SERVICE 401/403、200 accepted、删除缺文档 200；网关 404。
- Migration: N/A（V1 已由 BE-503 建表）。
- Error Case: search 不可达、Feign 读超时、事务回滚。
