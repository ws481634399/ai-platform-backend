# DU Task Spec — DU-BE-307

## 0. 元信息

- DU id: DU-BE-307
- Change ID: CHG-0012
- Feature Path: 商品与库存/商品发布与商城查询/商城查询/商城商品列表与详情查询
- 权威来源: story-design.md §5 / DU-BE-307

## 任务清单

- [ ] 任务 1 — ProductRepository.mallPage（ON_SALE 过滤）（verifies: TC-001, TC-002, TC-003）
- [ ] 任务 2 — MallProductController 列表与详情端点（verifies: TC-001, TC-004, TC-005, TC-006）

## Acceptance Criteria

- [ ] AC-001 — 列表只返回 ON_SALE
- [ ] AC-002 — 筛选与分页
- [ ] AC-003 — 按创建时间倒序
- [ ] AC-004 — 详情完整
- [ ] AC-005 — 非 ON_SALE 404
- [ ] AC-006 — 不含库存字段

## 执行顺序

1. 任务 1 → 2

## 并行度

无

## Verification

- API: MallProductApiTest（MockMvc，公开接口）
- 数据准备：多状态商品验证过滤
