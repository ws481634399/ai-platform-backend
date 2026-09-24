# DU Task Design — DU-BE-004

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §0（SSOT）；本文件只按 DU id DU-BE-004 引用该表，不新造 DU。
> 本文件固定为 Expected Implementation（Plan / Sketch / Pseudocode），
> 与 implementation.md（Actual Implementation）分立，不得合并。

## 1. Goal

用 RocketMQ 延迟消息替代定时扫描实现"创建订单→延迟到期→回查状态→自动取消"：建单同事务写延迟检查事件（超时动态可配、级别向上对齐），到期回查库内当前态、必经 OrderCancelService 取消；重复消息幂等；保留定时兜底扫描双路径 CAS 一致；admin 后端可查三态任务、手动取消并审计。覆盖 AC-026~031 及 AC-032 后端部分。

## 2. Repository

repo-1（implementation/ai-platform-backend）。

## 3. Scope

- mall-order 迁移 V5：outbox_event.delay_level；领域/PO/Mapper/repository/writer 全链承载。
- mall-order `application/order/timeout/`：PaymentTimeoutPolicy、DelayLevelMapper、OrderTimeoutFallbackScanner。
- mall-order 领域：OrderIntegrationEventType + Order.create 追加 PAYMENT_TIMEOUT_CHECK。
- mall-order `application/order/event/`：OrderEnvelopeAssembler、OutboxOrderEventFlusher 扩展。
- mall-order `application/outbox/`：EventRouter 注册、OutboxDeliveryTask 发送分支。
- mall-order `application/order/`：OrderCancelService systemCancel 重构。
- mall-order `application/order/consumer/`：PaymentTimeoutCheckHandler。
- mall-order `interfaces/rest/admin/`：DelayTaskAdminController + DelayTaskAdminMapper。
- mall-identity V14：order-delay 权限与菜单种子。
- mall-order application.yml：force-level/timeout-fallback 配置。

## 4. Design References

- requirement-design.md §2.1（模块树：OrderDelayCheckListener、DelayTaskAdminApi）、§2.1 备选方案（delayLevel 开箱、Outbox 全量走）、§4（契约）、§5.2（DU-BE-004 职责）。
- story-design.md §1（模块改动）、§2（接口契约）、§4（关键流程）。

## 5. Dependencies

- DU-BE-002：outbox_event/OutboxRecordWriter/EventRouter/OutboxDeliveryTask。
- DU-BE-003：Order 集成事件收集与事务 flush 挂点、findStatusById。
- 隐式依赖 DU-BE-001（Envelope/AbstractIntegrationHandler/IdempotentConsumer/rocketmq.enabled 装配）。

## 6. Implementation Sketch

- **delay_level 全链**：V5 ALTER TABLE 加列默认 0；OutboxEvent 构造/reconstitute/getDelayLevel；OutboxEventPo `@TableField("delay_level")`；save INSERT 列表含列；writer 四参 append；OutboxView 追加。
- **PaymentTimeoutPolicy**：`getLong("order.payment.timeout-minutes", defaultMinutes)`；provider 缺键/非法回退 + WARN 限流。
- **DelayLevelMapper**：静态级别秒数组 {1,5,10,30,60,...,7200}；目标秒 = timeoutMinutes×60；首个 ≥ 目标的级别，超表取 18；构造器 forceLevel（0=不强制）。
- **Order.create**：在 ORDER_CREATED 后 add PAYMENT_TIMEOUT_CHECK；两事件同一列表一次 flush。
- **assembler**：`assemble(order,event,timeoutMinutes)`；延迟事件 payload OrderDelayPayload、expireAt=createdAt.plus(timeoutMinutes,MINUTES)；ORDER_CREATED deadline 同值；toTag 增加分支。
- **flusher**：异步分支先读 timeoutMinutes 一次（times(1) 断言）；逐事件 assemble；延迟 append(level=forceLevel?:mapper.toLevel)，普通 append 三参；sync 全丢弃。
- **EventRouter**：register(PAYMENT_TIMEOUT_CHECK, new SendTarget(AIMALL_ORDER_DELAY,0))。
- **OutboxDeliveryTask.deliver**：`int level = target.isDelayed() ? target.delayLevel() : event.getDelayLevel(); level>0 ? sendDelay : sendSync`。
- **OrderCancelService**：`systemCancel(orderId,reason,source)` loadById（404）→ doCancel(order,"SYS:"+source,reason)；会员 cancel → loadOwned → doCancel(operator=memberId)；doCancel：ALREADY_TARGET 幂等、ILLEGAL 409、CAS transition、赢后 async 返回/sync releaseAfterCancel、CAS 输重读（CANCELLED 幂等/其他 409）。
- **PaymentTimeoutCheckHandler**：注解四件套；findStatusById：empty skip+WARN；非 PENDING skip；PENDING systemCancel；捕获 BusinessException STATUS_CONFLICT → markSkipped；其余抛出。
- **scanner**：@ConditionalOnProperty(mall.order.timeout-fallback.enabled,true,matchIfMissing=true)；@Scheduled fixedDelay；cutoff=now−policy.timeoutMinutes()；findExpiredPending(limit,cutoff) → systemCancel(...,TIMEOUT_FALLBACK)；try/catch 单条隔离。
- **findExpiredPending**：Mapper `@Select SELECT id,order_no FROM orders WHERE status='PENDING_PAYMENT' AND created_at < #{cutoff} LIMIT #{limit}`；端口返回 record ExpiredOrder(id,orderNo)。
- **DelayTaskAdminMapper**：union 三同构 SELECT（id, order_no, delay_status, created_at/NULL, NULL/cancelled_at, NULL/last_error）+ 外层 `<script>` WHERE delay_status 可选 + LIMIT OFFSET；count 查询包 union 派生表；LEFT JOIN outbox→orders。
- **controller**：page（order-delay:list）；POST cancel（order-delay:cancel）→ before 读 status → systemCancel(ADMIN_MANUAL) → audit（order-delay-audit logger：operator/orderId/before/after/traceId）。
- **V14**：仿 V13：2 权限 + 菜单页挂 /distributed（path /distributed/delay，component_key DelayTaskList）+ SUPER_ADMIN 权限/菜单授权。

## 7. Pseudocode

命中 orchestration + state-transition，必填。

```
// 建单事务内
repository.insert(order):
  insertOrders/Items/Histories; assignId
  events = order.pullIntegrationEvents()       // [ORDER_CREATED, PAYMENT_TIMEOUT_CHECK]
  if mode.async():
    minutes = policy.timeoutMinutes()          // 仅读取一次
    for e in events:
      env = assembler.assemble(order, e, minutes)
      level = e.type == PAYMENT_TIMEOUT_CHECK ? forceLevel ?: levelMapper.toLevel(minutes) : 0
      writer.append(order.id, e.tag, env, level)   // 失败回滚整事务
  // sync: 丢弃

// 到期消费
PaymentTimeoutCheckHandler.handle(env, p):
  statusOpt = orderRepo.findStatusById(long(p.orderId))
  if statusOpt.empty:
    markSkipped(eventId, group); warn("订单不存在"); return
  if statusOpt.get() != PENDING_PAYMENT:
    markSkipped(eventId, group); return
  try:
    cancelService.systemCancel(p.orderId, "PAYMENT_TIMEOUT", "DELAY_MESSAGE")
  catch BusinessException e when e.code == STATUS_CONFLICT:
    markSkipped(eventId, group)                // 状态已被支付/取消抢先

// 兜底扫描
scanner.scan():
  cutoff = now - policy.timeoutMinutes()
  for o in orderRepo.findExpiredPending(limit, cutoff):
    try: cancelService.systemCancel(o.id, "PAYMENT_TIMEOUT", "TIMEOUT_FALLBACK")
    catch Exception e: warn(o.id)              // 下轮重试；CAS 抢先幂等返回
```
