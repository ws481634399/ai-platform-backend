# DU Task Spec — DU-BE-703

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-703
- Change ID: CHG-0017
- Feature Path: 商城前台/商品浏览/商品列表与详情/商城商品列表
- 权威来源: story-design.md §5 / DU-BE-703

## 任务清单

- [ ] 任务 1 — 分页查询骨架 + size 1..50（51→400）（verifies: TC-001）
- [ ] 任务 2 — 子孙分类 id 集展开（三层取样命中）（verifies: TC-002）
- [ ] 任务 3 — brandIds 多选交集与 category 组合（verifies: TC-002, TC-003）
- [ ] 任务 4 — 四排序派生表 SQL（价区序+同分稳定），非法 sort 回落（verifies: TC-004, TC-005）
- [ ] 任务 5 — JSON 字段形态（id 字符串、价区整数分非 null）（verifies: TC-006）
- [ ] 任务 6 — 下架/无启用 SKU 商品全页剔除回归（verifies: TC-007）

## Acceptance Criteria

- [ ] AC-006 — 分页正确；size=51 → 400，50 正常。
- [ ] AC-007 — categoryId 命中子孙（三层验证）；brandIds 多选及组合取交集。
- [ ] AC-008 — default/newest 上架时间倒序；price_asc/desc 按 min/max 价区序且同分稳定；非法 sort 回落 default。
- [ ] AC-009 — records[].id 字符串；价区整数分非 null。
- [ ] AC-010 — 下架与无启用 SKU 商品任何页不出现。
- [ ] AC-020 — 金额整数分端到端（联调前端）。

## 执行顺序（Execution Order）

1. 任务 1 → 2/3 → 4 → 5/6。

## 并行度（Parallelization）

任务 2（分类展开）与任务 3（品牌）可并行开发。

## Verification

- Unit: 参数解析/收敛/排序枚举映射；子孙展开夹具。
- Integration: 三层分类与品牌交集夹具；价区序与 tie-breaker；全页翻页过滤。
- API: 200/400 契约与 JSON 类型断言（JSON 字面量 "id":"..."）。
- Migration: N/A。
- Error Case: 不存在 categoryId 空页；超长 IN 收敛。
