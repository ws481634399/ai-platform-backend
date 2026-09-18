# DU Task Spec — DU-BE-502

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本 DU 对应 STORY-005-01-02-01。

## 0. 元信息

- DU id: DU-BE-502
- Change ID: CHG-0020
- Feature Path: 商品搜索/商品搜索查询/关键词搜索与筛选/商品关键词搜索
- 权威来源: STORY-005-01-02-01 story-design.md §5 / DU-BE-502

## 任务清单

- [ ] 任务 1 — domain 查询模型（SearchQuery/SearchPage/SortMode/ProductSearchItem/SearchDocument 映射）（verifies: TC-010@02-01）
- [ ] 任务 2 — ES Repository multi_match 四字段+name^3、ON_SALE 过滤、hits 映射（verifies: TC-001, TC-002, TC-003@02-01）
- [ ] 任务 3 — 分页（默认20/上限100/page 回退）与无 keyword 浏览态（verifies: TC-004, TC-005, TC-009@02-01）
- [ ] 任务 4 — MallSearchController + UnifyResult（verifies: TC-009@02-01）
- [ ] 任务 5 — 网关 /api/mall/search/** permitAll 路由（verifies: TC-006, TC-007@02-01）
- [ ] 任务 6 — 故障口径回归 B0501（verifies: TC-008@02-01）

## Acceptance Criteria

STORY-005-01-02-01（关键词搜索）：

- [ ] AC-001 — 关键词命中 name/keywords/brandName/categoryName 均可检出。
- [ ] AC-002 — OFF_SALE 文档任何查询不返回。
- [ ] AC-003 — 响应仅摘要字段，无 SKU 等聚合字段。
- [ ] AC-004 — 分页切片/total 正确；size=500 限 100；page=0/-1 回退第 1 页。
- [ ] AC-005 — 无 keyword 返回 ON_SALE default 浏览结果。
- [ ] AC-006 — 网关无 token 200（不 401）。
- [ ] AC-007 — /api/internal/** 经网关 404；无可达写端点。
- [ ] AC-008 — ES 故障 B05xx 口径一致。

## 执行顺序（Execution Order）

1. 任务 1→2→3→4（查询链路）→ 5/6（网关与故障回归）。

## 并行度（Parallelization）

任务 5（网关）与 2–4 可并行。

## Verification

- Unit: 分页归一/mapper 单测。
- Integration: Testcontainers ES 造数（含下架商品）验证命中字段、ON_SALE 过滤、分页 total。
- API: MockMvc 200/503 结构；网关 E2E（Integration Gate）匿名 200、internal 404。
- Migration: N/A。
- Error Case: ES 停服 B0501、越界分页回退。
