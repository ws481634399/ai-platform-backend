# DU Task Spec — DU-BE-907

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5 / DU-BE-907。

## 0. 元信息

- DU id: DU-BE-907
- Change ID: CHG-0019
- Feature Path: 订单交易/订单查询与履约/后台订单履约/后台订单查询与发货
- 权威来源: story-design.md §5 / DU-BE-907

## 任务清单

- [x] 任务 1 — AdminOrderController 列表/详情端点 + 方法级 order:list/view 权限（verifies: TC-001, TC-002, TC-006）
- [x] 任务 2 — ShipmentService.ship：PAID→SHIPPED CAS、物流字段校验、operator、history(SHIP)（verifies: TC-003, TC-004, TC-005）
- [x] 任务 3 — mall-identity V8：order:list/view/ship/compensation 权限 + 订单目录/页面菜单 + SUPER_ADMIN 授权（verifies: TC-006, TC-007）
- [x] 任务 4 — 网关 /api/admin/orders/** ADMIN 路由（verifies: TC-006）

## Acceptance Criteria

- [x] AC-001 — 多条件组合检索（orderNo/memberId/status/时间）正确，分页排序正确。
- [x] AC-002 — 详情字段完整，history 可用于轨迹展示。
- [x] AC-003 — PAID 发货成功 → SHIPPED、delivery_company/tracking_no/shippedAt、history(SHIP,operator)。
- [x] AC-004 — PENDING/CANCELLED/COMPLETED 发货 → B0407；重复发货幂等返回且仅一条 SHIP 历史。
- [x] AC-005 — 物流字段缺失/超长 → 400；订单不存在 → 404。
- [x] AC-006 — 无 order:ship 权限管理员 → 403；MEMBER 访问 admin 接口 → 403。

## 执行顺序

1. 任务 3（权限迁移）→ 任务 1/2 → 任务 4。

## Verification

- Integration: OrderApiTest admin 段（多条件检索、发货成功/非法态/重复幂等、权限 403）。
- Migration: mall-identity V8 在 H2 验证 8/8 迁移成功。
- API: 200/400/403/404/409 契约。
