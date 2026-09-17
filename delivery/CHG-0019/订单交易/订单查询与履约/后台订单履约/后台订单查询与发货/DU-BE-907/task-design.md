# DU Task Design — DU-BE-907

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。
> 注：task 阶段遗漏物化，dev 阶段补建；代码已在 M4 提交 a06ed4c 内完成。

## 1. Goal

后台订单多条件检索与详情、PAID→SHIPPED 发货（物流信息/操作人）、RBAC 权限码 order:list/view/ship，订单状态集中状态机 CAS 幂等。

## 2. Repository

repo-1（mall-order + mall-identity V8 权限菜单）

## 3. Scope

- AdminOrderController：GET /api/admin/orders（orderNo/memberId/status/startAt/endAt + 分页）、GET /{orderNo}、POST /{orderNo}/ship；方法级 @PreAuthorize order:list/view/ship。
- ShipmentService：加载订单 → 归属/存在校验 → OrderStatus.evaluate(PAID, SHIP)：MUTATED 走 CAS UPDATE PAID→SHIPPED + delivery_company/tracking_no/shippedAt + history(SHIP, operator=当前管理员账号)；ALREADY_TARGET 幂等 200；ILLEGAL B0407。
- 物流字段：deliveryCompany/trackingNo 必填、@Size(max=64)，缺失/超长 400。
- mall-identity V8 Flyway：order:list/view/ship/compensation 权限点 + 订单管理目录 + OrderList/CompensationList 页面菜单 + SUPER_ADMIN 授权（幂等 SQL）。
- 网关：/api/admin/orders/** → 8105 ADMIN 鉴权链。
- 越权/不存在统一 B0401 404；无权限 403（OrderWebExceptionHandler AccessDenied→403 业务包体）。

## 4. Design References

- requirement-design.md §4.4、STORY-004-03-02-01 story-design.md §1~§4。

## 5. Dependencies

- 运行时依赖：DU-BE-903（订单状态机）、DU-BE-904（订单查询聚合）；本表 depends on 记"无"因跨 Story 依赖不入 DU 表。
- 权限依赖：mall-identity V8 迁移。

## 6. Implementation Sketch

- 列表与详情复用 OrderQueryService（admin 版无 memberId 过滤），OrderViewAssembler 输出同构。
- 发货 operator 取 SecurityContext 管理员账号（sub claim）。
- 重复发货：CAS rows=0 重读，若已 SHIPPED 返回 ALREADY_TARGET 200，不新增 history。

## 7. Pseudocode

N/A。状态机 CAS 与 pay/cancel 同构，见 OrderStatus.evaluate。
