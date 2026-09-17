# DU Task Design — DU-BE-903

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

支付/取消的集中状态机编排（CAS 仲裁 + 事务外库存副作用 + 幂等解读），并原子加固 mall-inventory 的 release/confirm，消除并发重复增减库存隐患。

## 2. Repository

repo-1（mall-order + mall-inventory 改造；响应契约不变）

## 3. Scope

- domain.order：Order.pay()：status==PENDING_PAYMENT → 内部迁移事件（to=PAID,op=PAY）；==PAID → 幂等标记；else 抛 ORDER_STATUS_NOT_ALLOWED。cancel(reason) 同构（to=CANCELLED,op=CANCEL，写 reason）。
- OrderRepository：`transitionStatus(cmd)` @Transactional：UPDATE orders CAS（SET status=:to, version=version+1, <timeCol>=NOW(6) [,cancel_reason/delivery 列] WHERE id=:id AND status=:from AND version=:version）+ insert history（from/to/operation/operator/reason/occurred_at）；返回 rows。
- PaymentService：loadForMember(orderNo,memberId)（empty→ORDER_NOT_FOUND 404，不区分越权）→ 聚合解释：
  - PAID → 直接返回（幂等，无库存调用）；
  - PENDING → transitionStatus：rows=1 → 提交后逐行 inventoryPort.confirm(`orderNo:skuId`)；任一异常 catch 记录 ERROR（orderNo/traceId/reservationId）但不回滚、返回 PAID 视图；rows=0 → 重读：PAID 幂等返回 / CANCELLED → B0407；
  - 其他 → B0407。
- OrderCancelService 镜像（时间列 cancelled_at；release 逐行，异常同策略）。
- MemberOrderController：POST /{orderNo}/pay；POST /{orderNo}/cancel（@Valid CancelRequest{@Size(max=255) reason}）。
- mall-inventory：
  - InventoryMapper 新增 `releaseStock(skuId,qty)`：`... SET locked_quantity=locked_quantity-#{qty}, updated_at=NOW() WHERE sku_id=#{skuId} AND locked_quantity>=#{qty}`；`deductStock(skuId,qty)`：total/locked 同减同条件；InventoryReservationMapper `casStatus(id,from,to)`。
  - InventoryApplicationService.release/confirm 重写 @Transactional：加载 reservation → 已目标态：直接返回当前视图（无 stock/log 写入）→ 冲突态（release 遇 DEDUCTED 等）：抛业务冲突 → casStatus(LOCKED→目标) rows=1：releaseStock/deductStock 必须 rows=1（否则抛异常回滚）、写库存变动日志、返回；rows=0：重读回前两分支。
  - lock 路径不变（已是条件 insert/update，实施时核对 available/locked 守卫）。

## 4. Design References

- requirement-design.md §2.3（集中状态机与并发）、§2.4（副作用顺序与不回滚原则）；STORY-004-02-01-01 story-design.md §1/§2。

## 5. Dependencies

权威表：DU-BE-902。inventory 内部 confirm/release 端点已存在（CHG-0016），本 DU 仅改实现。

## 6. Implementation Sketch

- operator：history.operator 写会员主体 id 字符串；admin 场景（发货）写 username（DU-BE-905）。
- 事务外调用：service 类不加 @Transactional；仅 repository.transitionStatus 开启短事务，HTTP 调用不在事务内。
- CAS rows=0 重读解释模式统一为私有方法 `reinterpretAfterCasLost(order, expectedOp)`。
- inventory 条件更新保证不会出现 locked 扣成负数；stock 更新 rows=0 与 reservation CAS 结果不一致时抛异常使 @Transactional 回滚（理论不可达，作为不变量护栏）。
- ERROR 日志埋点封装私有方法（STORY-004-04-01-01 替换为 CompensationService.compensateOnceOrRecord）。

## 7. Pseudocode

```
pay(orderNo, memberId):
  order = repo.findForMember(orderNo, memberId) ?: throw 404
  if order.status == PAID: return view(order)              // 幂等
  if order.status != PENDING: throw B0407
  rows = repo.transitionStatus(order.id, PENDING, PAID, op=PAY, time=paidAt)
  if rows == 0:                                            // 竞争失败
      fresh = repo.findByOrderNo(orderNo)
      if fresh.status == PAID: return view(fresh)
      throw B0407                                          // 被取消抢占
  for r in order.items:
      try inventoryPort.confirm(orderNo+":"+r.skuId)
      catch e: log.error(orderNo, traceId, reservationId)   // 不回滚；DU-BE-906 接补偿
  return view(reload(orderNo))
```
