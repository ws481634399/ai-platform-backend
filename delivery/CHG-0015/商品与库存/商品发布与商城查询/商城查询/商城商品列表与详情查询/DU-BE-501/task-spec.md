# DU Task Spec — DU-BE-501

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-501
- Change ID: CHG-0015
- Feature Path: 商品与库存/商品发布与商城查询/商城查询/商城商品列表与详情查询
- 权威来源: story-design.md §5 / DU-BE-501

## 任务清单

- [ ] 任务 1 — 新增 @StringId 元注解并全量标注 product/inventory 对外 DTO 业务 ID（verifies: TC-006, TC-007, TC-008）
- [ ] 任务 2 — mall-common-security 实现 InternalIdentityFilter/Properties，product/inventory internal 包接入；SkuClient 携带 X-Internal-Token（verifies: TC-004, TC-013）
- [ ] 任务 3 — 网关白名单显式化 + /api/internal/** denyAll（verifies: TC-001, TC-002, TC-003）
- [ ] 任务 4 — mall-inventory 注册分页插件修复 total（verifies: TC-005）
- [ ] 任务 5 — 价区分组 SQL + EXISTS 有效 SKU 过滤并填充列表（verifies: TC-009, TC-010, TC-014）

## Acceptance Criteria

- [ ] AC-001 — 网关匿名 GET /api/mall/products 200 有数据。
- [ ] AC-002 — 匿名详情 200；不存在/下架 404/不可售。
- [ ] AC-003 — 外网 /api/internal/** 被 denyAll（404 同构体）。
- [ ] AC-004 — inventory 初始化真实 SKU 成功（凭证齐全）。
- [ ] AC-005 — 库存分页 total 与行数一致且翻页恒定。
- [ ] AC-006 — 商品 id JSON 为字符串不丢精度。
- [ ] AC-007 — 全部对外业务 ID 字符串；金额/数量/分页仍为 number。
- [ ] AC-008 — 入参 "123"/123 双形态等价。
- [ ] AC-009 — min/maxPrice 为启用 SKU 真实整数分。
- [ ] AC-010 — 无启用 SKU 商品不返回。
- [ ] AC-012 — skuId 字符串经网关端到端无末位偏差（与 FE 联调）。

## 执行顺序（Execution Order）

1. 任务 1（注解）→ 2（凭证）→ 3（网关）→ 4（分页）→ 5（价区），可部分并行：1 与 2 独立。

## 并行度（Parallelization）

任务 1 与任务 2/4 可并行；任务 3 依赖 2 的路径约定（可同时编码）。

## Verification

- Unit: InternalIdentityFilter 三态、@StringId 序列化断言。
- Integration: 价区分组 SQL/EXISTS；分页 total；入参双形态。
- API: 经网关 8080 匿名 200、internal 404；内部端点无头 401/有头 200。
- Migration: N/A。
- Error Case: product client 故障语义不改变（503 与 NOT_SALABLE 区分）。
