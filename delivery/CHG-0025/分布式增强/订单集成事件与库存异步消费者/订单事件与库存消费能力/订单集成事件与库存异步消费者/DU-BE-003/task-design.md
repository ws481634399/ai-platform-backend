# DU Task Design — DU-BE-003

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-003 引用该表，不新造 DU。
> 本文件固定为 Expected Implementation（Plan / Sketch / Pseudocode），
> 与 implementation.md（Actual Implementation）分立，不得合并。

## 1. Goal

把订单支付/取消与库存 confirm/release 的同步强耦升级为事件驱动最终一致：mall-order 四个状态迁移事务内经 S2 Outbox 原子发布订单事件；mall-inventory 以 S1 SPI 幂等消费 PAYMENT_SUCCEEDED/ORDER_CANCELLED，含乱序裁决、失败登记补偿；MQ 关闭时降级 M4 同步路径。覆盖 AC-018~025。

## 2. Repository

repo-1（implementation/ai-platform-backend）。

## 3. Scope

- mall-order `domain/order/event/`：OrderIntegrationEventType、OrderIntegrationEvent；Order 聚合事件收集（create/pay/cancel/confirmReceipt）+ pullIntegrationEvents。
- mall-order `application/order/event/`：OrderEventOutbox 端口、OutboxOrderEventFlusher、OrderEnvelopeAssembler、IntegrationMode。
- mall-order 仓储：MyBatisOrderRepository insert/transition 事务内 flush 挂点（构造器新增依赖）。
- mall-order 应用服务：PaymentService、OrderCancelService 异步/降级分支。
- mall-order `interfaces/rest/internal/`：OrderStatusInternalController、CompensationInternalController + DTO。
- mall-inventory `infrastructure/client/OrderServiceClient.java`；仓储 findReservationIdsByOrderNo（@Select LIKE）。
- mall-inventory `consumer/`：PaymentSucceededInventoryHandler、OrderCancelledInventoryHandler。
- mall-inventory pom：mall-event-contracts + mall-common-mq；两侧 application.yml：rocketmq 段 + order-uri。

## 4. Design References

- requirement-design.md §2.1（模块树：4 发布点、internal 状态端点、消费者落点）、§2.1 备选方案（乱序裁决/DLQ 衔接定稿）、§4（Event/API/Data Contract）。
- story-design.md §1（模块改动）、§2（接口契约）、§4（关键流程）。

## 5. Dependencies

- DU-BE-002（OutboxRecordWriter / outbox_event / EventRouter）。
- 隐式依赖 DU-BE-001 交付物（Envelope、AbstractIntegrationHandler、IdempotentConsumer、rocketmq.enabled 装配）。

## 6. Implementation Sketch

- **Order 聚合**：integrationEvents 列表；四个迁移点各 add 对应类型事件；reconstitute 初始化为空；pull 取出并清空。CAS 落败对象整体丢弃。
- **OrderEnvelopeAssembler.assemble(order, event)**：payload DTO（reservationNo=orderNo；paymentNo="PAY"+orderNo；金额 money.payFen/CNY；paymentDeadline=createdAt+timeout-minutes）→ valueToTree → Envelope builder（UUID/eventVersion=1/producer=mall-order/traceId=TraceContext.get）。
- **OutboxOrderEventFlusher.flush(order)**：pull 事件；async 时逐事件 assembler + writer.append(aggregateId=orderId)（MANDATORY，同事务回滚）；sync 丢弃。
- **MyBatisOrderRepository**：insert id 回填/行/历史落库后 flush；transition rows==1 插 history 后 flush。
- **PaymentService/OrderCancelService**：注入 IntegrationMode；async CAS 赢后直接返回；sync 走 confirmAfterPaid/releaseAfterCancel（既有私有方法保留）+ 降级 WARN。
- **internal**：GET status（按 id，404）；POST compensations（type → compensationPort 对应方法，参数校验）。
- **OrderServiceClient**：RestClient X-Internal-Token；getStatus / registerCompensation。
- **PaymentSucceededInventoryHandler**：回查状态 → CANCELLED markSkipped+WARN；否则枚举 orderNo:% 预留逐行直接调 confirmDeduction（应用服务对 DEDUCTED 幂等返回）；confirm 异常登记补偿后抛出。
- **OrderCancelledInventoryHandler**：CANCELLED 逐行直接调 release（RELEASED 幂等返回）；COMPLETED/PAID/SHIPPED markSkipped+WARN；异常登记补偿后抛出。
- Handler 均 @ConditionalOnProperty(rocketmq.enabled=true)。

## 7. Pseudocode

命中 orchestration + state-transition，必填。

```
// 发布侧（事务内）
repository.transition(change):
  rows = CAS(...)
  if rows == 1:
    insertHistory(...)
    events = order.events()           // 迁移时收集
    if mode.async():
      for e in events:
        env = assembler.assemble(order, e)
        writer.append(order.id, e.tag, env)   // 失败回滚整事务
    // sync: 事件丢弃
  return rows == 1

// 消费侧
PaymentSucceededInventoryHandler.handle(env, p):
  status = orderClient.getStatus(p.orderId)   // 失败抛→重试
  if status == CANCELLED:
    markSkipped(env.eventId, group); warn; return
  for rid in reservationRepo.findIdsByOrderNo(p.orderNo):
    try: inventoryService.confirmDeduction(ConfirmCommand(rid))  // DEDUCTED 幂等返回
    catch e:
      orderClient.registerCompensation(p.orderNo, INVENTORY_CONFIRM_DEDUCT, [line])
      throw
```
