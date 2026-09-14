# DU Task Spec — DU-BE-802

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-802
- Change ID: CHG-0018
- Feature Path: 商城前台/购物车/会员购物车/购物车实时校验
- 权威来源: story-design.md §5 / DU-BE-802

## 任务清单

- [ ] 任务 1 — GET /cart 读模型装配（图/名/规格/价/数量/选中）（verifies: TC-001）
- [ ] 任务 2 — itemStatus 失效矩阵（下架/禁用/缺失）（verifies: TC-002）
- [ ] 任务 3 — PRICE_CHANGED 识别与最新价展示；忽略请求 price（verifies: TC-003）
- [ ] 任务 4 — 库存阈值三态（0/1/9/10）（verifies: TC-004）
- [ ] 任务 5 — product 全 UNKNOWN、inventory 仅 stockStatus UNKNOWN 条目级降级 200（verifies: TC-005）
- [ ] 任务 6 — selectedTotalFen/selectedCount 口径（整数分）（verifies: TC-006）
- [ ] 任务 7 — 两次批量调用计数无 N+1（≤100）（verifies: TC-007）
- [ ] 任务 8 — 读后 Redis 字段与 TTL 不变（只读）（verifies: TC-008）
- [ ] 任务 9 — 跨库禁令审计（无 JDBC/数据源，仅 RestClient）（verifies: TC-009）

## Acceptance Criteria

- [ ] AC-008 — 读车字段齐全，价格为 product 整数分；product/inventory 各一次调用。
- [ ] AC-009 — 三类失效状态正确；读校验不改写 Redis。
- [ ] AC-010 — 改价 PRICE_CHANGED + 最新价；请求 price 忽略。
- [ ] AC-011 — 库存 0/1-9/≥10 三态正确。
- [ ] AC-012 — product 故障全条目 UNKNOWN 且 200；inventory 故障仅库存 UNKNOWN。
- [ ] AC-013 — 合计仅 VALID+选中+有货，整数分，count 正确。
- [ ] AC-021 — mall-cart 不直连 product/inventory 数据源。

## 执行顺序（Execution Order）

1. 任务 1 → 2/3/4 → 5/6 → 7/8/9。

## 并行度（Parallelization）

任务 3/4（状态映射）可并行。

## Verification

- Unit: 装配器状态矩阵与合计夹具；降级两分支。
- Integration: 两次 MockRestServiceServer 各断言调用 1 次；Redis 前后 dump/TTL 比对。
- API: GET 200 + 双状态 JSON。
- Migration: N/A。
- Error Case: 对端 5xx/超时条目级降级；空车 {items:[],total 0}。
