# DU Task Spec — DU-BE-904

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-904 引用该表，不新造 DU。

## 0. 元信息

- DU id: DU-BE-904
- Change ID: CHG-0019
- Feature Path: 订单交易/订单查询与履约/会员订单查询与确认收货/会员订单列表与详情
- 权威来源: story-design.md §5 / DU-BE-904

## 任务清单

- [ ] 任务 1 — OrderQueryService + Repository pageForMember（member 强制过滤、status/时间区间、created_at,id 倒序、分页上限 100）（verifies: TC-001, TC-002）
- [ ] 任务 2 — findDetailByOrderNoForMember（三表装配；不区分越权/不存在→empty）与详情服务（verifies: TC-003, TC-004）
- [ ] 任务 3 — GET /api/mall/orders 列表端点 + SummaryView（firstItem/itemCount）（verifies: TC-001, TC-002, TC-010）
- [ ] 任务 4 — GET /api/mall/orders/{orderNo} 详情端点 + DetailView assembler（快照直出、history 升序、无下游调用）（verifies: TC-003）
- [ ] 任务 5 — 参数校验：status 枚举/时间区间/分页；401/403/404 矩阵（verifies: TC-002, TC-004）

## Acceptance Criteria

- [ ] AC-001 — 多订单倒序分页正确，total/pages 准确，size 上限 100。
- [ ] AC-002 — 六 Tab 与状态映射正确；非法 status 400；时间区间过滤正确；start>end 400。
- [ ] AC-003 — 详情全字段快照与升序 history，读库不调下游。
- [ ] AC-004 — 列表/详情强制本人；猜 orderNo 404；未认证 401；ADMIN 403。

## 执行顺序（Execution Order）

1. 任务 1/2 → 3/4 → 5。

## 并行度（Parallelization）

任务 1 与任务 2 可并行。

## Verification

- Unit: assembler/Summary firstItem 选取单测。
- Integration: H2 直接 Mapper 造 15 单跨两会员；双 JWT 归属矩阵；断言无 RestClient 交互（mock bean verifyNoInteractions）。
- API: 200/400/401/403/404 契约。
- Migration: N/A。
- Error Case: 越权统一 404 文案；非法时间/枚举 400。
