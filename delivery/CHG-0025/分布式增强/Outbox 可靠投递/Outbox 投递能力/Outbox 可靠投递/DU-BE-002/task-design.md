# DU Task Design — DU-BE-002

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-002 引用该表，不新造 DU。

## 1. Goal

实现 Outbox 可靠投递核心机制：业务事务内原子写入 outbox_event、独立调度任务 CAS 抢占式投递、有界退避、同聚合顺序、eventType 路由，以及 mall-admin 后端 Outbox 查询/重投 API。覆盖 AC-010~015、AC-017。

## 2. Repository

repo-1（implementation/ai-platform-backend）。

## 3. Scope

- mall-order 模块：`com.aimall.mall.order.outbox` 包（OutboxStatus 枚举、OutboxEvent 实体、OutboxEventRepository、OutboxRecordWriter、OutboxDeliveryTask、OutboxBackoffPolicy、EventRouter/SendTarget）。
- mall-order Flyway：`add_outbox_event` 迁移（outbox_event 表 + 索引）。
- mall-admin 后端：`OutboxAdminController`（/api/admin/outbox/events）、DTO、RBAC 权限码（system:outbox:list / system:outbox:retry）、审计。
- mall-identity：权限码 + 菜单 DML。

## 4. Design References

- requirement-design.md §3（发送可靠性定稿 / CAS 抢占 / 同聚合顺序 / 延迟路由）、§4（Event Contract / Data Contract）、§5（outbox_event DDL）。
- story-design.md §1（模块改动）、§2（接口契约细化）、§3（数据变更）、§4（关键流程）。

## 5. Dependencies

- DU-BE-001（IntegrationEventProducer.sendSync / Envelope / rocketmq.enabled 开关）。

## 6. Implementation Sketch

- **OutboxRecordWriter.append(aggregateId, eventType, envelope)**：在调用方事务内构造 OutboxEvent（status=PENDING、retryCount=0、nextRetryAt=null、payload=envelope JSON、traceId=envelope.traceId）并 save；失败抛异常触发业务回滚。
- **OutboxDeliveryTask.run()**（@Scheduled fixedDelay 5s）：
  1. findPendingDue(batch=100, order by aggregate_id, created_at)；
  2. 按 aggregateId 分组，每组取首条；
  3. claim(id) CAS（UPDATE ... SET status='SENDING' WHERE id=? AND status='PENDING'），影响行数 0 则跳过；
  4. route(eventType) → SendTarget{topic, delayLevel}（订单四事件→aimall-order-events, delayLevel=0）；
  5. integrationEventProducer.sendSync(envelope, target.topic, target.delayLevel)；
  6. 成功 → markSent(id)；失败 → markFailedWithBackoff(id, err, nextRetryAt, retryCount+1)（未超限回 PENDING+退避，超限 FAILED）。
- **OutboxBackoffPolicy**：nextRetryAt = now + min(initialDelay * factor^retryCount, maxDelay)；maxRetries 默认 10。
- **EventRouter**：Map<String,SendTarget>；@PostConstruct 注册订单事件路由；register() 供 STORY-009-04-01 扩展延迟路由。
- **OutboxAdminController**：GET 列表/详情（分页+筛选）；POST /{id}/retry → resetForRetry（FAILED→PENDING，retry_count 清零）+ 审计。
- 任务对 rocketmq.enabled=false / MQ 连接异常统一按失败退避保留 PENDING，不标 FAILED。

## 7. Pseudocode

命中 state-transition + orchestration，必填。

```
OutboxDeliveryTask.run():
  rows = repo.findPendingDue(PageRequest.of(0, batchSize))
  groups = groupByAggregateIdOrderByCreatedAtAsc(rows)
  for (firstOfGroup : groups):
    claimed = repo.claim(firstOfGroup.id)   // CAS: PENDING -> SENDING
    if claimed == 0: continue
    try:
      env = deserialize(firstOfGroup.payload)
      target = eventRouter.route(firstOfGroup.eventType, env)
      producer.sendSync(env, target.topic, target.delayLevel)
      repo.markSent(firstOfGroup.id)         // SENDING -> SENT
    catch Exception e:
      nextRetry = backoff.nextRetryAt(firstOfGroup.retryCount + 1)
      if firstOfGroup.retryCount + 1 >= maxRetries:
        repo.markFailed(firstOfGroup.id, e.message)   // -> FAILED
      else:
        repo.requeueWithBackoff(firstOfGroup.id, e.message, nextRetry, firstOfGroup.retryCount+1)  // -> PENDING
```
