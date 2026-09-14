# DU Task Spec — DU-BE-704

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-704
- Change ID: CHG-0017
- Feature Path: 商城前台/商品浏览/商品列表与详情/商品详情与 SKU 选择
- 权威来源: story-design.md §5 / DU-BE-704

## 任务清单

- [ ] 任务 1 — 详情接口主信息/图集/brandName/categoryPath/富文本装配（verifies: TC-001）
- [ ] 任务 2 — specDimensions 归并 + skuIndex 装配（维度/值顺序、组合唯一、status 透传）（verifies: TC-001, TC-008）
- [ ] 任务 3 — 不存在/下架/无启用 SKU 统一 404 口径（verifies: TC-003）
- [ ] 任务 4 — 脏数据防御：组合冲突取一 warn 不 500；缺规格 SKU 跳过矩阵（verifies: TC-009）

## Acceptance Criteria

- [ ] AC-011 — 详情字段齐全；skuId 字符串、价格整数分；矩阵顺序与组合唯一。
- [ ] AC-013 — 不存在/不可售直访 → 404 商品页（后端 404 码）。

## 执行顺序（Execution Order）

1. 任务 1 → 2 → 3/4。

## 并行度（Parallelization）

任务 3（口径）与任务 4（防御夹具）可并行。

## Verification

- Unit: DetailAssembler 矩阵夹具（多维多值、禁用 SKU、冲突组合、缺规格）。
- Integration: @SpringBootTest 详情全字段；分类路径上溯；404 三类。
- API: JSON 类型（skuId 字符串）与 404 体。
- Migration: N/A。
- Error Case: 脏数据不 500；brand/category 缺链部分降级。
