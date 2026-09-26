# DU Task Design — DU-BE-005

> 层级：实现仓侧产物——Expected Implementation 之"怎么做"（落地草图与伪码）。
> 权威设计：外部 story-design.md；本文件不复制全文，只给落地骨架。

## 1. Goal

为 M7 异步消费补齐幂等实体表与 ORDER_AUTO_CANCEL 补偿类型，增强管理台筛选/人工完成与审计。

## 2. Repository

repo-1（mall-inventory、mall-order、mall-identity 同仓）。

## 3. Scope

- 新增：V3/V6 consumed_event 迁移；CompensationActionHandler 接口；OrderAutoCancelCompensationHandler/Payload；V15 identity。
- 修改：CompensationTask（常量+manualComplete）、InventoryCompensationHandler（implements）、CompensationService（登记/列表/MDC）、PaymentTimeoutCheckHandler（失败登记）、CompensationRepository(+MyBatis)（page 参数）、AdminCompensationController（筛选/complete/审计）、CompensationView DTO（payload）。

## 4. Design References

- 外部：stories/STORY-009-05-01/story-design.md §1~§4、test-design.md TC-001~011
- 决策：requirement-design.md §2（决策 5 幂等/决策 7 DLQ 衔接）、§4 DDL

## 5. Dependencies

DU-BE-003（消费者/补偿登记）、DU-BE-004（延迟回查/systemCancel）。

## 6. Implementation Sketch

```
mall-inventory/resources/db/migration/V3__create_consumed_event.sql   # 见 story §1.1 DDL
mall-order/resources/db/migration/V6__create_consumed_event.sql       # 同构

domain/compensation/CompensationTask.java
  + String OP_AUTO_CANCEL_ORDER = "AUTO_CANCEL_ORDER"
  + void manualComplete(Instant now)   // status=SUCCESS; nextRetryAt=null;
                                       // lastError = prefix("MANUAL_COMPLETE@", lastError)

application/compensation/
  CompensationActionHandler.java       # NEW interface: supports(String)/handle(task)
  InventoryCompensationHandler.java    # implements（既有逻辑搬移）
  OrderAutoCancelCompensationPayload.java  # NEW record(long orderId, String orderNo, String eventId)
  OrderAutoCancelCompensationHandler.java   # NEW @Component
  CompensationService.java             # 见 §7

application/order/consumer/PaymentTimeoutCheckHandler.java
  // catch (RuntimeException ex) when not STATUS_CONFLICT:
  //   compensationService.enqueueOrderAutoCancel(orderId, orderNo, msg, MDC traceId); throw ex;

domain/compensation/CompensationRepository.java
  CompensationPage page(String businessType, String businessId, String status, int page, int size)
infrastructure/.../MyBatisCompensationRepository.java
  // wrapper.eq(type non-blank, businessType).like(id non-blank, businessId)

interfaces/rest/admin/AdminCompensationController.java
  // GET: + operation(白名单) + aggregateId(escape LIKE)
  // POST /{id}/complete: manualComplete + audit
  // audit logger "compensation-audit": operator/id/before/after/at
interfaces/rest/admin/dto/AdminOrderDtos.java
  // CompensationView + String payload
mall-identity/db/migration/V15__compensation_manage_permissions.sql
```

## 7. Pseudocode

```text
// CompensationService 构造
handlers: List<CompensationActionHandler>   // Spring 注入全部实现

execute(task):
  trace = task.traceId()
  if trace != null: MDC.put(KEY, trace)
  try:
    h = handlers.stream().filter(supports(task.operation())).findFirst()
        .orElseThrow(IllegalStateException)
    h.handle(task)
    task.markSuccess(now)
  catch ex:
    task.recordFailure(rootMessage(ex), now)
  finally:
    repository.update(task)
    if trace != null: MDC.remove(KEY)

enqueueOrderAutoCancel(orderId, orderNo, reason, traceId):
  payload = json(OrderAutoCancelCompensationPayload(orderId, orderNo, eventId=traceId-bound?))
  task = CompensationTask.register(TYPE_ORDER, orderNo, OP_AUTO_CANCEL_ORDER, payload, traceId, now)
  inserted = repository.insertIgnore(task)
  log.warn(... inserted)

// Controller complete
complete(id):
  task = repo.findById(id).orElseThrow(404)
  if task.status == SUCCESS: return task
  before = task.status
  task.manualComplete(now)
  repo.update(task)
  audit.info(operator, id, before, SUCCESS, at)
  return task
```

## 8. 测试落点

见外部 test-design TC-001~011；TDD 红灯先行，一任务一提交。
