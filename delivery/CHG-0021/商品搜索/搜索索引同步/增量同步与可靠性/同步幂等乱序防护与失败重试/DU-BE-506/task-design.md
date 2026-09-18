# DU Task Design — DU-BE-506

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

external_gte 毫秒版本乱序防护（冲突消化为成功）、failure_record 有界退避重试调度（30s/1m/2m/5m/10m，5 次 FAILED_DEAD）、失败记录管理端点（人工重试）。

## 2. Repository

repo-1（mall-services/mall-search）

## 3. Scope

- upsert：IndexRequest.version(payload.updatedAtMillis).versionType(EXTERNAL_GTE)；catch VersionConflictEngineException → INFO 日志按成功处理（不落失败表）。
- application.search.SearchSyncFailureService：
  - recordFailure：同 (product_id,op) PENDING 行存在则更新 payload/reason/updated_at，否则插入 PENDING、retry_count=0、next_retry_at=now+30s；
  - @Scheduled(fixedDelay=30s) scanAndRetry：status=PENDING AND next_retry_at<=now LIMIT 100；逐条重放；单条异常不影响批次；
    成功→SUCCEEDED；失败 retry_count+1：≥5→FAILED_DEAD，否则 next_retry_at=now+BACKOFF[retry_count]（[30s,1m,2m,5m,10m]）；
  - 人工 retry(id)：存在→PENDING、next_retry_at=now 立即拾取；不存在→B0504 SYNC_FAILURE_NOT_FOUND 404；
  - 重放时重新拉投影：下架→DELETE；上架→UPSERT。
- @EnableScheduling（配置/主类开启；注释标明单实例限制，多实例需 ShedLock）。
- SearchIndexAdminController：GET /api/admin/search/sync-failures（status 筛选分页，search:index:list）、POST sync-failures/{id}/retry（search:index:rebuild）。

## 4. Design References

- CHG-0021 requirement-design.md §2.3（版本/退避/终态）；STORY-005-02-02-02 story-design §1–§4。

## 5. Dependencies

权威表：无。实际前置 DU-BE-505；V1 failure_record 表由 DU-BE-503 提供。

## 6. Implementation Sketch

```
upsert(doc, versionMillis):
  try: es.index(EXTERNAL_GTE, versionMillis)
  catch VersionConflictEngineException:
       log.info("stale event ignored product={} version={}", doc.productId, versionMillis)  // 视为成功

@Scheduled scanAndRetry(now):
  rows = repo.findPending(now, 100)
  for r in rows:
    try: replay(r)                 // 重新拉投影：在售 upsert / 下架 delete
         repo.markSuccess(r.id)
    catch e:
      n = r.retryCount+1
      if n>=5: repo.markDead(r.id)
      else: repo.schedule(r.id, n, now + BACKOFF[n-1])

replay(r):
  p = productClient.single(r.productId)
  if p==null: es.delete(id) else: es.index(EXTERNAL_GTE, p.updatedAt, doc=p)
```

## 7. Pseudocode

```
recordFailure(productId, op, payload, reason, now):
  existing = repo.findPending(productId, op)
  if existing: repo.updatePayload(existing.id, payload, reason, now)
  else: repo.insert(PENDING, productId, op, payload, reason,
                    retryCount=0, nextRetryAt=now+30s)

manualRetry(id):
  r = repo.findById(id) ?: throw SYNC_FAILURE_NOT_FOUND   // B0504
  repo.markPending(r.id, now=clock.millis())              // 下次扫描立即拾取
```
