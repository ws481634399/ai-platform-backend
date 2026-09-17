# DU Task Design — DU-BE-905

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部两 Story 的 story-design.md §5（SSOT）；DU 跨确认收货 + 后台发货。

## 1. Goal

订单履约两个写动作（会员确认收货 COMPLETED、admin 发货 SHIPPED）与 admin 只读检索，全部走集中状态机 CAS，方法级权限码保护。

## 2. Repository

repo-1（mall-order）

## 3. Scope

- domain.order：
  - Order.confirmReceipt()：SHIPPED→COMPLETED（completedAt=now，op=CONFIRM_RECEIPT）；COMPLETED 幂等标记；其余 STATUS_NOT_ALLOWED。
  - Order.ship(deliveryCompany,trackingNo)：PAID→SHIPPED（shippedAt，op=SHIP，携带物流两列）；SHIPPED 幂等（忽略新物流值，以首次为准）；其余拒绝。
- application.order：
  - ReceiptService.confirm(orderNo,memberId)：loadForMember 404 归一 → 解释 → transitionStatus（时间列 completed_at，无库存调用）→ rows=0 重读解释。
  - OrderQueryService：pageForAdmin(AdminOrderPageQuery)（order_no 精确、member_id 精确、status、created_at 区间，倒序分页）；getForAdmin(orderNo)（无 member 条件，404 仅不存在）。
  - ShipmentService.ship(orderNo,ShipRequest,operator)：加载 404 → 聚合解释 → CAS 更新同时 SET delivery_company/tracking_no（PAID→SHIPPED 首次携带；幂等路径不改列）→ rows=0 重读（SHIPPED 幂等 / 其他 B0407）。
- interfaces.rest.admin.AdminOrderController（/api/admin/orders）：
  - GET 列表 @PreAuthorize("hasAuthority('order:list')")；
  - GET /{orderNo} order:view；
  - POST /{orderNo}/ship order:ship，@Valid ShipRequest{@NotBlank @Size(max=64) deliveryCompany,trackingNo}；operator 取 SecurityContext username/subject。
- DTO：AdminOrderSummaryView（SummaryView + memberId 字符串）；复用 OrderDetailView。

## 4. Design References

- requirement-design.md §2.3（状态机）、§4.4（履约规则/权限码）；STORY-004-03-01-02 与 STORY-004-03-02-01 story-design.md。

## 5. Dependencies

权威表：DU-BE-903、DU-BE-904。权限机制依赖 CHG-0015 RBAC（方法级 @PreAuthorize authority 命名实施时与 mall-admin 现有权限码对齐）。

## 6. Implementation Sketch

- transitionStatus cmd 扩展为可选附加列（deliveryCompany/trackingNo/cancelReason），CAS 成功路径才写附加列。
- 收货无任何库存/下游 HTTP；断言测试 verifyNoInteractions(inventoryPort)。
- admin 分页复用 PageView；查询条件空值全可选。
- 权限测试构造不同 scope/authority 的 ADMIN JWT（参考既有 admin 服务测试写法）。

## 7. Pseudocode

```
ship(orderNo, req, operator):
  order = repo.findByOrderNo(orderNo) ?: throw 404
  if order.status == SHIPPED: return view(order)          // 幂等，忽略 req 差异
  if order.status != PAID: throw B0407
  rows = repo.transitionStatus(id, PAID, SHIPPED, op=SHIP,
          time=shippedAt, extras={deliveryCompany,trackingNo}, operator)
  if rows == 0:
      fresh = reload; SHIPPED -> view(fresh); else -> B0407
  return view(reload)

confirmReceipt(orderNo, memberId):
  order = repo.findForMember(orderNo, memberId) ?: throw 404
  if COMPLETED: return view(order)
  if not SHIPPED: throw B0407
  CAS SHIPPED->COMPLETED (completed_at) ; rows=0 -> 重读解释
```
