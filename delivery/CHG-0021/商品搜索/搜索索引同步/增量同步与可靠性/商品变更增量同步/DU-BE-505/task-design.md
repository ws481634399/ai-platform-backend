# DU Task Design — DU-BE-505

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

商品变更增量同步链路：mall-product 事务后事件（7 写方法）+ SearchSyncClient；mall-search 内部 upsert/硬删除端点（ES 故障受理 200，落表在 BE-506 完整接入）。

## 2. Repository

repo-1（mall-services/mall-product、mall-services/mall-search）

## 3. Scope

- mall-product domain.product.event.ProductSearchChangedEvent（productId/op[UPSERT|DELETE]/occurredAt epoch milli）。
- 在 ProductApplicationService 7 个 @Transactional 写方法中发布事件：
  create/update/publish/addSku/updateSku/changeSkuStatus(→启用态) UPSERT；unpublish/changeStatus(→下架) DELETE（changeStatus 按目标状态分支）。
- application.product.search.ProductSearchSyncListener：@TransactionalEventListener(AFTER_COMMIT) → SearchSyncClient；try/catch 全捕获 log.error（含 productId/op/traceId），绝不外抛。
- infrastructure.client.SearchSyncClient（@FeignClient name=mall-search contextId=searchSync，connectTimeout 1s/readTimeout 3s）：sync(ProductSearchProjection)、delete(productId)。
- mall-search interfaces.rest.internal.InternalSearchSyncController（SERVICE）：
  - POST /api/internal/search/products/sync → SearchSyncApplicationService.upsert（id=productId，external_gte version 逻辑在 BE-506）；ES 故障 → failureRecordService.record（BE-506）+ 返回 200 {accepted:true}；
  - DELETE /api/internal/search/products/{id} → 硬删除（缺文档幂等成功）。
- mapper 复用 DU-BE-503 的 Projection→SearchDocument 映射。

## 4. Design References

- CHG-0021 requirement-design.md §2.3（增量链路/事件/受理语义）；STORY-005-02-02-01 story-design §1/§2/§4。

## 5. Dependencies

权威表：无。实际前置 DU-BE-503；与 DU-BE-506 同迭代按序实施（BE-505→BE-506）。

## 6. Implementation Sketch

```
ProductApplicationService.write(...) [@Transactional]
  applicationEventPublisher.publishEvent(ProductSearchChangedEvent)   // 事务内仅登记
      ↓ commit 后
ProductSearchSyncListener.onChanged(e) [AFTER_COMMIT]
  try:
    if e.op == UPSERT: p = projectionClient.self? —— product 端已有聚合：直接组装投影载荷
                      searchSyncClient.sync(payload)
    else:             searchSyncClient.delete(e.productId)
  catch Exception: log.error（不抛；事务已提交）

InternalSearchSyncController.sync(payload) [SERVICE]
  try: syncAppService.upsert(payload)  // docId=productId
  catch ES故障: failureRecordService.record(UPSERT,payload,reason)  // BE-506
  return {accepted:true}
```

说明：product 端组装载荷优先复用本地聚合查询（避免回调自身）；若信息不全（如名称/图），由 product 侧既有 productSku 仓储读取，与 search-projection 端点同口径。

## 7. Pseudocode

```
afterCommit(e):
  try:
    if e.op == UPSERT:
       payload = projectionAssembler.assemble(e.productId)   // ON_SALE 无启用 SKU 时 payload=null
       if payload == null: client.delete(e.productId)        // 不可售→删除
       else: client.sync(payload)
    else:
       client.delete(e.productId)
  catch Exception ex:
    log.error("search sync failed after commit product={} op={} trace={}",
              e.productId, e.op, MDC.traceId, ex)            // 吞掉

onSync(payload):
  try:
    es.index(i -> i.index(ALIAS).id(String.valueOf(payload.productId))
                        .document(toDoc(payload)))
  catch ElasticsearchException|TransportException ex:
    failureRecord.record(payload.productId, UPSERT, json(payload), reasonOf(ex))
  return accepted
```
