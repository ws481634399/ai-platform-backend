# DU Task Spec — DU-BE-511

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本 DU 对应 STORY-005-01-02-02。

## 0. 元信息

- DU id: DU-BE-511
- Change ID: CHG-0020
- Feature Path: 商品搜索/商品搜索查询/关键词搜索与筛选/搜索筛选与排序
- 权威来源: STORY-005-01-02-02 story-design.md §5 / DU-BE-511

## 任务清单

- [ ] 任务 1 — SearchQuery/SortMode 扩展（categoryId/brandId/minPriceFen/maxPriceFen/sort 三排序值）（verifies: TC-008@02-02）
- [ ] 任务 2 — categoryId/brandId 过滤与叠加交集（verifies: TC-001@02-02）
- [ ] 任务 3 — 价格区间相交过滤（边界命中、单边生效、跨价区间 SKU 商品）（verifies: TC-002@02-02）
- [ ] 任务 4 — keyword+品牌+价格组合取交集（verifies: TC-003@02-02）
- [ ] 任务 5 — 四排序（price 严格序、newest publishedAt null 排尾）（verifies: TC-004@02-02）
- [ ] 任务 6 — 非法 sort 静默回退 default 且 default 稳定（verifies: TC-005@02-02）
- [ ] 任务 7 — 价格参数校验 400 B0502（min>max/非数字/负数）（verifies: TC-006@02-02）
- [ ] 任务 8 — 筛选+排序+分页组合切片 total 与全量计算一致（verifies: TC-007@02-02）

## Acceptance Criteria

STORY-005-01-02-02（筛选与排序）：

- [ ] AC-001 — categoryId/brandId 过滤正确。
- [ ] AC-002 — 价格闭区间边界命中；单边生效；区间相交语义。
- [ ] AC-003 — 关键词+品牌+价格组合取交集。
- [ ] AC-004 — price_asc/desc 严格序；newest 按发布时间倒序。
- [ ] AC-005 — 非法 sort 回退 default 不报错；default 稳定。
- [ ] AC-006 — min>max/非数字/负数 → 400 统一结构。
- [ ] AC-007 — 筛选+排序+分页 total/切片正确，ON_SALE 恒生效。

## 执行顺序（Execution Order）

1. 任务 1 → 2/3 → 4 → 5/6 → 7/8（随 DU-BE-502 主链路同模块发布）。

## 并行度（Parallelization）

任务 7（参数校验单测）可与 2–5 并行。

## Verification

- Unit: QueryNormalizeTest（排序映射/非法回退/区间校验）。
- Integration: Testcontainers ES 造数矩阵（2 类目/3 品牌/多价格段/含未发布）逐条断言过滤与严格序。
- API: MockMvc 200/400 B0502 统一结构。
- Migration: N/A。
- Error Case: min>max、负值、非数字绑定失败、非法 sort。
