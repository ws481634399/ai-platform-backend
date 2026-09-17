# DU Task Design — DU-BE-906

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

同步交易链路的基本异常恢复：CompensationTask 落表 + 指数退避有界重试调度 + admin 查询/人工重试，替换前序 Story 的 ERROR-only 挂点；补偿幂等、可观测。

## 2. Repository

repo-1（mall-order）

## 3. Scope

- V2__compensation_init.sql：id bigint PK 雪花；business_type varchar(48)、business_id varchar(64)（orderNo）、operation varchar(48)（INVENTORY_RELEASE/INVENTORY_CONFIRM）、payload text（{"reservationIds":[...]}）、status varchar(16)、retry_count int default 0、max_retries int default 5、last_error varchar(1000)、next_retry_at datetime(6)、created_at/updated_at；uk_business_op(business_type,business_id,operation)、idx_status_next(status,next_retry_at)。
- domain.compensation：CompensationTask 聚合（recordFailure(error) 计算 backoff[30s,60s,120s,300s,600s] 与 FAILED_DEAD 转换；markSuccess；manualReset；mergePayload 去重合并 reservationIds）；BusinessType 枚举（ORDER_CREATE/ORDER_PAY/ORDER_CANCEL）；TaskStatus（PENDING/SUCCESS/FAILED_DEAD）；CompensationOperation。
- infrastructure.persistence.compensation：Po/Mapper（insert ignore 语义用 INSERT + catch DuplicateKey 后 select；updateCas：WHERE id AND status='PENDING' 防并发拾取）；MyBatisCompensationRepository（insertIgnoreOrGet、findDue(now,limit)、findById、page(status,page,size)）。
- application.compensation：
  - InventoryCompensationHandler.handle(task)：解析 reservationIds 逐个 inventoryPort.release/confirm；端口返回/查询当前 reservation 状态：已是目标态→该条成功；任一条失败→整体失败（下次继续全量重试，靠幂等保证安全）。
  - CompensationService.compensateOnceOrRecord(type,orderNo,op,reservationIds,action)：先执行 action（同步尝试一次）；成功直接返回；异常/失败 → insertIgnoreOrGet（冲突则 mergePayload）→ recordFailure+upsert（PENDING,next_retry_at=now+30s,retry_count=1 或沿用既有计数）→ WARN 结构化日志。
  - runDueTask(task)：handler 执行 → 成功 markSuccess；失败 recordFailure（退避/DEAD + ERROR when DEAD）；更新走条件 status='PENDING'。
  - manualRetry(id)：SUCCESS → 400 ALREADY_SUCCESS；不存在 404；reset（retry_count=0,status=PENDING,next_retry_at=now,last_error=null）后立即 runDueTask。
  - pageQuery(status,page,size)。
- interfaces.rest.admin.AdminCompensationController：GET /api/admin/compensations；POST /api/admin/compensations/{id}/retry；@PreAuthorize("hasAuthority('order:compensation')")；CompensationView。
- scheduling：MallOrderApplication @EnableScheduling；CompensationRetryScheduler @Component @ConditionalOnProperty("mall.order.compensation.scheduler-enabled", havingValue="true", matchIfMissing=true)；@Scheduled(fixedDelayString="${mall.order.compensation.scan-delay-ms:30000}")；findDue(now, ${...scan-limit:50}) 逐条 try/catch runDueTask。
- 接线：OrderCreateService（锁中途失败已锁 release、落库异常 release）、PaymentService（confirm 异常）、OrderCancelService（release 异常）统一改 compensateOnceOrRecord；action 封装为逐行端口调用。
- 日志：MDC traceId（项目既有链路约定，实施核对）；字段 taskId/businessType/orderNo/operation/retryCount/result/error（截断）；payload 仅 reservationId/orderNo，天然无 PII/secret。

## 4. Design References

- requirement-design.md §2.4（创建链路与补偿编排）、§2.9（补偿与可观测）、§4.5（REQ-M4-004/管理端点）；STORY-004-04-01-01 story-design.md 全文。

## 5. Dependencies

权威表：DU-BE-903。inventory release/confirm 已在 DU-BE-903 具备目标态幂等语义。

## 6. Implementation Sketch

- 业务键：businessType=ORDER_CREATE/ORDER_PAY/ORDER_CANCEL，businessId=orderNo，operation=INVENTORY_RELEASE/INVENTORY_CONFIRM——支付确认与取消释放可能针对同单先后出现（终态互斥使只会有一方真正需要补偿），uk 允许两行不同 operation 共存。
- 退避：backoff(n)=[30s,1m,2m,5m,10m][min(retryCount-1,4)]；recordFailure 后若 retryCount>=maxRetries → FAILED_DEAD（next_retry_at 置空）。
- 同步尝试语义：action 内部已含"逐行 best-effort"；compensateOnceOrRecord 中 action 抛异常/返回失败才落任务；全部成功不落表（场景 A 正路径）。
- 调度单实例假设：不引入 ShedLock（M7 前单实例部署）；条件更新 status='PENDING' 为多实例留最小护栏。
- 人工 retry 立即执行的结果如实返回（仍失败 → FAILED_DEAD/退避视图）。

## 7. Pseudocode

```
compensateOnceOrRecord(type, orderNo, op, ids, action):
  try action(ids); return                       // 同步成功，无任务
  catch e:
    task = repo.insertIgnoreOrGet(type, orderNo, op, ids)  // 冲突 -> merge ids
    task.recordFailure(e, backoff=30s)         // retry_count 1 起
    repo.upsert(task); log.warn(...)

scanDue():
  for t in repo.findDue(now, 50):
    try:
       ok = handler.handle(t)                  // 全量逐条，目标态算成功
       ok ? t.markSuccess() : t.recordFailure(backoff[n])
       repo.updateIfPending(t)
    catch ex: t.recordFailure(...); repo.updateIfPending(t); log.error

manualRetry(id):
  t = repo.findById(id) ?: 404
  if t.status == SUCCESS: throw 400
  t.resetForManual(); repo.update(t)
  runDueTask(t); return view(t)
```
