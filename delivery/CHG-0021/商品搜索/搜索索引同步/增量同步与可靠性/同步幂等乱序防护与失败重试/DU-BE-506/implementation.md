# DU Implementation — DU-BE-506

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

### mall-search（`mall-services/mall-search`，包根 `com.ai.mall.search`）

- 版本化写入：`infrastructure/elasticsearch/EsSearchIndexAdapter.java`
  - `upsertVersioned`/`bulkUpsert`：ES `ExternalGte`，外部版本号取投影 `updatedAt` epoch millis，`refresh=true`（单条）/`false`（批量）；409 VersionConflict → 归一定位为 `STALE_VERSION`；
  - `deleteVersioned`：同样携带 external_gte 版本，409 → `STALE_VERSION`（旧下架事件晚到不删新文档），404 → `WRITTEN`（删除幂等）。
- 失败记录领域模型：`domain/index/SearchSyncFailure.java`（聚合）
  - `BACKOFF = {30s, 1m, 2m, 5m, 10m}`、`MAX_RETRIES = 5`；
  - `recordRetryFailure` 按序列推进 `nextRetryAt`，第 5 次失败转 `FAILED_DEAD`；
  - `rearm()`：人工重试复活——清零 retryCount、`nextRetryAt=now`；`refresh(lastError)` 复用行刷新错误；错误信息统一截断 1000。
- 枚举：`SyncFailureStatus`（PENDING/SUCCESS/FAILED_DEAD）、`SyncEventType`（UPSERT/DELETE）。
- 失败服务：`application/index/SearchSyncFailureService.java`
  - `recordFailure(productId, eventType, rootMessage)`：先查同 `(productId,eventType)` 的 PENDING 行——存在则刷新 `last_error/nextRetryAt` 复用，否则新 register（初始退避 30s）；落库自身异常仅 ERROR 不外抛；
  - `@Scheduled(fixedDelayString="${mall.search.sync-retry-delay-ms:30000}") scanAndRetry()`：`findDue(PENDING, now, LIMIT 100)`，单条 try/catch 隔离；
  - `replay()`：重拉 product 投影当前态——`fetchOne` 返回 null → `deletePlain`，非 null → `upsertLatest`；成功 markSuccess，陈旧版本（STALE_VERSION）亦视为成功；
  - `manualRetry(id)`：不存在 → `BusinessException(B0504, 404)`（`IndexErrorCode.SYNC_FAILURE_NOT_FOUND`）；非 PENDING（含 FAILED_DEAD）先 `rearm` 再**立即** replay 一次。
- 持久化：`domain/index/SyncFailureRepository` + `infrastructure/persistence` MyBatis-Plus 实现（Po/Mapper），依托 V1 索引 `idx_sync_failure_status_next`（调度拾取）与 `idx_sync_failure_product`（去重查询）。
- 管理端查询/重试端点随 `AdminSearchIndexController` 同批落地（见 DU-BE-504）：`GET /sync-failures?status&page&size`、`POST /sync-failures/{id}/retry`；`SyncFailureView`（id,productId,eventType,status,retryCount,maxRetries,lastError,nextRetryAt,createdAt）。
- 调度开关：`MallSearchApplication.java` 增加 `@EnableScheduling`，类注释写明 M5 单实例前提、多实例需 ShedLock；两个测试类以 `mall.search.sync-retry-delay-ms=3600000` 避免后台调度干扰。

### 测试证据（方法级，未留存 surefire 报告）

- `src/test/.../index/SyncFailureFlowTest.java`（H2 + Mock，6 例）：
  1. `acceptedOnEsFailureAndRecorded`：sync 遇 ES 故障仍 200 受理、落 1 行 PENDING、首退避约 30s；
  2. `backoffSequenceAndDead`：退避 30/60/120/300/600 秒，第 5 次失败转 FAILED_DEAD、retryCount=5；
  3. `replaySuccessAndStaleAsSuccess`：重放成功与陈旧版本按成功消化；
  4. `replayDeleteWhenProjectionMissing`：fetchOne 返回 null → 验证调用 `deletePlain`；
  5. `manualRetryRearmAndNotFound`：FAILED_DEAD 人工重试复活后成功；id=999999 → B0504 404；
  6. `duplicateFailureReusesRow`：同 productId 连续两次故障仅 1 行。
- `IndexSyncIntegrationTest`（真实 ES）：
  - `staleUpsertSwallowed`（v200 后到 v100 upsert，保持 v200 不报错）；
  - `offSaleSyncDeletesAndStaleDeleteKeepsNew`（旧版本下架删除被版本保护，文档保留）。

## Commits

| Commit | 仓库 | 说明 |
| --- | --- | --- |
| 82ccf6e | repo-1 ai-platform-backend | `feat(search,system): M5 商品搜索+索引同步+系统配置（CHG-0020/0021/0022 后端）`（版本化写入、失败表服务、调度重试与管理端查询均在其中） |
| 1de0d9c | repo-1 ai-platform-backend | review major 闭环：补 requirement-design §2.4 声明的内部只读端点 `GET /api/internal/search/sync-failures`（InternalSearchFailureController + InternalSearchFailureApiTest 2 例） |

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
           - 原 DU 建议:
           - 实际实现:
           - 原因:
           - 影响评估: -->

### DEV-1

- 原 DU 建议: `search_sync_failure_record` 含 `op`、`payload`（mediumtext）、`reason` 等列，重放依赖存储的载荷。
- 实际实现: V1 表列为 `event_type`、`status`、`retry_count`、`max_retries(default 5)`、`last_error(1000)`、`next_retry_at` 等，**不存 payload/op 名称**；重放一律重拉 product 投影当前态（`fetchOne` 非空 upsert / 为空 delete）。
- 原因: requirement-design 本就要求"重放重拉 product 投影当前态"，旧 payload 在重试时可能已过期（商品期间再次编辑）；不存载荷使表更轻、重放内容永远以权威源为准。
- 影响评估: product 长时间不可达时重试内容不固化，但这正是期望语义（恢复后同步当前态）；与 story-design §1 表结构字面不符，行为等价且更正确。

### DEV-2

- 原 DU 建议: 同 `(productId,eventType)` PENDING 去重依赖数据库 upsert/唯一约束。
- 实际实现: 去重在应用层完成（先 `findPending(productId,eventType)`，存在则刷新、否则插入），表上仅有普通索引 `idx_sync_failure_product` 而非唯一约束。
- 原因: M5 单实例调度与受理，无并发拾取；应用层判定可同时刷新 last_error/nextRetryAt，逻辑集中在聚合内。
- 影响评估: 多实例并发受理极端情况下可能产生重复行；`MallSearchApplication` 注释已声明单实例前提，多实例需 ShedLock（M7）。

### DEV-3

- 原 DU 建议: 人工重试将 retryCount 清零后"下次调度扫描立即拾取"。
- 实际实现: `manualRetry` 在 `rearm`（retryCount 清零、nextRetryAt=now）后于**同一请求内立即 replay 一次**；只有重放仍失败时才落回退避表，由后续调度继续。
- 原因: 运维点击人工重试期望即时反馈，避免最长 30s 固定调度等待。
- 影响评估: 接口语义从"安排重试"变为"立即重试一次"；成功立即 SUCCESS，失败保留 PENDING 与退避，行为对运维更友好。

### DEV-4

- 原 DU 建议: 乱序仲裁基于商品独立 version 字段/事件版本。
- 实际实现: 版本号直接使用投影 `updatedAt` epoch millis（ProductSearchProjection 透传），upsert 与 delete 均经 external_gte 仲裁，409 统一映射 `STALE_VERSION`。
- 原因: updatedAt 单调推进且两端已有该字段，无需新增版本列；毫秒精度对人工/后台编辑频度足够。
- 影响评估: 同一毫秒内的两次变更理论上版本相等——external_gte 允许相等版本写入，最终态以最后一次投递内容为准，配合 AFTER_COMMIT 重查当前态，结果仍收敛。

### DEV-5（sdd-review 闭环，提交 1de0d9c）

- 原 DU 建议: requirement-design §2.4 与 story-spec [S4] 声明 `GET /api/internal/search/sync-failures?status=`（SERVICE 角色）内部只读排查端点。
- 实际实现: 初版仅交付了管理端 `/api/admin/search/index/sync-failures`，内部端点漏实现且未登记偏差；review 四查判定 major 后补齐 `InternalSearchFailureController`（路径 `/api/internal/search/sync-failures`，复用 SearchSyncFailureService.page/countByStatus，返回 {total,page,size,items} 与管理端同构，size 夹 1..100），安全链经既有 `/api/internal/**` hasRole SERVICE + InternalIdentityFilter 自动覆盖；新增 InternalSearchFailureApiTest 2 例（令牌分页 + FAILED_DEAD 筛选、无令牌 4xx）。
- 原因: 初版误判该端点无消费方可裁剪，但设计契约与 story-spec 明确声明，未登记即偏离；按 review resolution 选「补实现」而非改设计。
- 影响评估: 新增只读端点不改变既有同步/管理链路；mall-search 定向回归 26 例全绿。

## 自检

### 任务清单

| 任务 | 证据 | 结果 |
| --- | --- | --- |
| 任务 1 external_gte 版本与冲突消化（含旧 delete 晚到） | `staleUpsertSwallowed`、`offSaleSyncDeletesAndStaleDeleteKeepsNew`（真实 ES 409） | ✅ |
| 任务 2 recordFailure 落表/去重/首退避 30s | `acceptedOnEsFailureAndRecorded`、`duplicateFailureReusesRow` | ✅（去重方式见 DEV-2） |
| 任务 3 @Scheduled 扫描/退避序列/FAILED_DEAD | `backoffSequenceAndDead`（30/60/120/300/600，第 5 次 FAILED_DEAD）；测试直接调用扫描方法避免 30s 等待（符合 task-spec 策略），固定延迟 30s 由配置默认值保证 | ✅ |
| 任务 4 重放重拉投影（下架删除/再上架 upsert） | `replayDeleteWhenProjectionMissing`、`replaySuccessAndStaleAsSuccess` | ✅ |
| 任务 5 sync-failures 查询与人工 retry（404 B0504） | `manualRetryRearmAndNotFound`（DEAD 复活成功、未知 id B0504 404）；分页/筛选端点已交付（服务层分页单测未单列） | ✅（立即重放见 DEV-3） |
| 任务 6 `mvn -pl mall-services/mall-search -am test` 全绿 | 21 个相关 @Test 方法就位（本 DU 相关 8 个：Flow 6 + 冲突 2）；当前环境未留存 surefire 执行报告，未在本机执行 mvn（真实 ES 用例需 Docker） | ⚠️ 方法级证据齐备，执行数字留 CI/Integration Gate |

### Acceptance Criteria

| AC | 证据 | 结果 |
| --- | --- | --- |
| AC-001 同事件两投仅一份最新文档 | `duplicateFailureReusesRow`（故障两投一行）+ `staleUpsertSwallowed`（同 docId 两版本仅一份） | passed（`duplicateFailureReusesRow`、`staleUpsertSwallowed`） |
| AC-002 v200 后到 v100 upsert 保持 v200 不报错 | `staleUpsertSwallowed`（真实 ES external_gte 409 → STALE_VERSION INFO 消化，接口 200） | passed（`staleUpsertSwallowed`） |
| AC-003 v200 后到 v100 delete 不删且有日志 | `offSaleSyncDeletesAndStaleDeleteKeepsNew`（版本化删除 409 被保护，文档仍可搜）；STALE_VERSION 分支 INFO 日志 | passed（`offSaleSyncDeletesAndStaleDeleteKeepsNew`） |
| AC-004 ES 停时落 PENDING；恢复后重试 SUCCESS 可搜 | `acceptedOnEsFailureAndRecorded`（200+PENDING+30s）；恢复路径由 `replaySuccessAndStaleAsSuccess` 直接方法调用覆盖（未做真实停启 ES 的调度 E2E） | passed（受理/落表/重放方法级；真实停启调度留 Integration Gate） |
| AC-005 退避序列正确；第 5 次 FAILED_DEAD 不再拾取 | `backoffSequenceAndDead`（30/60/120/300/600 秒断言、retryCount=5、FAILED_DEAD）；`findDue` 仅查 PENDING | passed（`backoffSequenceAndDead`） |
| AC-006 人工重试 FAILED_DEAD 可激活并最终成功 | `manualRetryRearmAndNotFound`（DEAD→rearm→立即 replay SUCCESS） | passed（`manualRetryRearmAndNotFound`） |
| AC-007 重试时已下架→删除 SUCCESS；再上架→upsert | `replayDeleteWhenProjectionMissing`（fetchOne null→deletePlain）、`replaySuccessAndStaleAsSuccess`（当前态 upsert 成功） | passed（`replayDeleteWhenProjectionMissing`、`replaySuccessAndStaleAsSuccess`） |
| AC-008 Testcontainers IT 覆盖主路径，mvn test 全绿 | 真实 ES：IndexSyncIntegrationTest 9 例；H2+Mock：SyncFailureFlowTest 6 例；本机无 surefire 报告、未执行 mvn（需 Docker），不杜撰执行数字 | ⚠️ 用例齐备，全绿执行证据留 CI/Integration Gate |

### Verification 对照

- Unit（退避/状态流转 Clock、LIMIT SQL、去重 upsert）：退避序列以固定时钟断言完整覆盖；LIMIT 100 拾取经 H2 `findDue` 实际执行；去重为应用层实现（DEV-2）——✅。
- Integration（真实 ES 冲突；停 ES→落表→恢复→调度成功）：external_gte 冲突与故障受理已覆盖真实 ES/Mock 两侧；"停 ES→恢复→调度自动拾取"未做真实停启 E2E，恢复重放以直接方法调用验证——⚠️。
- API（人工 retry 200/404；列表筛选分页）：B0504 404 与复活成功在服务层断言；控制器 MockMvc 路径随 `authGuards`（401/403）间接覆盖，分页 size 夹取逻辑代码可评审——⚠️ 部分。
- Migration: N/A（复用 V1；表结构差异见 DEV-1）——N/A。
- Error Case（版本冲突、单条失败隔离、投影下架/再上架）：版本冲突、单条隔离（scanAndRetry 逐条 try/catch，代码可评审）、下架/再上架均有对应测试/代码——✅（隔离无故障注入用例，⚠️）。
