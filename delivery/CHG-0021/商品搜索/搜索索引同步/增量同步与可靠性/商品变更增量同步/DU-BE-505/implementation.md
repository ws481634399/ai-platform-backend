# DU Implementation — DU-BE-505

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

### mall-product（`mall-services/mall-product`，包根 `com.ai.mall.product`）

- 应用事件：`application/search/ProductSearchChangedEvent.java` 为 `record(Long productId, String operation)`——不携带 op 枚举与 payload，仅做"该商品已变更"通知。
- 发布点（`application/service/ProductApplicationService.java`，共 **8** 处）：CREATE(L191)、UPDATE(L205)、CHANGE_STATUS(L218)、ADD_SKU(L235)、UPDATE_SKU(L244)、CHANGE_SKU_STATUS(L257)、PUBLISH(L267)、UNPUBLISH(L275)；不按目标状态预选 op，统一下发后由监听器重查当前态判定。
- 监听器：`application/search/ProductSearchSyncListener.java`
  - `@TransactionalEventListener(phase = AFTER_COMMIT)`：仅事务提交后触发，事务回滚零调用；
  - 重新调用 `projectionService.findById(productId)`：非 null → `SearchSyncClient.sync(view)`（仅在架投影可查出），null → `client.delete(productId)`（草稿/下架/无启用 SKU 统一删除语义）；
  - 全部异常 catch 仅 `log.error`（含 productId、operation、traceId），不外抛，不影响已提交的商品写接口。
- 同步客户端：`infrastructure/client/SearchSyncClient.java` 使用 Spring `RestClient`（connect **1s** / read **3s**，`X-Internal-Token`，base `mall.product.search-service-uri:http://localhost:8107`），POST `/api/internal/search/products/sync`、DELETE `/api/internal/search/products/{id}`，响应解包 `UnifyResult`。
- 配置：`src/main/resources/application.yml` 增加 `mall.product.search-service-uri`。
- 投影复用：监听器重查复用 DU-BE-503 的 `ProductSearchProjectionService` 与 `SearchProjectionView`（13 字段，updatedAt epoch millis 供 search 端版本仲裁）。

### mall-search（`mall-services/mall-search`，包根 `com.ai.mall.search`）

- 内部端点：`interfaces/rest/internal/InternalSearchSyncController.java`（`/api/internal/search/products`）
  - `POST /sync`：受理投影，恒返回 200 `{accepted:true}`；
  - `DELETE /{productId}`：无版本硬删除（`deletePlain`，缺文档幂等），恒 200 `{accepted:true}`。
- 受理服务：`application/index/SyncReceiveService.java`（implements 端口 `SyncWriteExecutor`）
  - `receive()`：投影 `status=ON_SALE` → `upsertVersioned`（external_gte 版本=updatedAt），否则 `deleteVersioned`（版本化删除，防旧下架事件删掉新文档）；
  - ES 返回 `STALE_VERSION`（409）→ INFO「过期同步事件已消化」，按成功处理；
  - 其他 Exception → WARN + `failureService.recordFailure(productId, eventType, rootMessage)`（落表逻辑见 DU-BE-506），接口仍 200 受理；
  - `receiveDelete()`（DELETE 端点）→ `deletePlain` 幂等删除。

### 测试证据（方法级，未留存 surefire 报告）

- mall-product `src/test/.../application/search/ProductSearchSyncEventTest.java`（2 例）：
  - `createPublishUnpublishEventChain`：草稿 CREATE → delete；publish → sync（`argThat` 断言 ON_SALE、minPrice 8800）；unpublish → delete（含草稿删除共 2 次 delete）；
  - `listenerFailureNeverBreaksWriteApi`：`client.sync` 抛异常时 publish 接口仍返回 200、主事务数据正常。
- mall-product `ProductSearchProjectionApiTest`（4 例，投影端点联调复用）：`projectionPageOnSaleOnly`、`paginationOrderAndSize`、`projectionWithoutTokenUnauthorized`、`singleProjectionAndNotFoundCases`。
- mall-search `IndexSyncIntegrationTest` 相关：`syncUpsertAcceptedAndSearchable`（200 受理 + 文档可搜）、`offSaleSyncDeletesAndStaleDeleteKeepsNew`、`deleteEndpointIdempotent`、`staleUpsertSwallowed`、`authGuards`（内部端点无凭证 401）。

## Commits

| Commit | 仓库 | 说明 |
| --- | --- | --- |
| 82ccf6e | repo-1 ai-platform-backend | `feat(search,system): M5 商品搜索+索引同步+系统配置（CHG-0020/0021/0022 后端）`（product 事件/监听器/客户端与 search 内部同步端点均在其中） |

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
           - 原 DU 建议:
           - 实际实现:
           - 原因:
           - 影响评估: -->

### DEV-1

- 原 DU 建议: 事件携带 op 映射（UPSERT/DELETE）与载荷，`changeStatus` 在发布点按目标状态分支决定 op；监听器按事件 op 执行。
- 实际实现: `ProductSearchChangedEvent` 仅 `(productId, operation 描述串)`；监听器在 AFTER_COMMIT **重查当前投影**——查到在架投影即 upsert、查无即 delete；`operation` 仅用于日志。
- 原因: 避免在事务进行中读取/组装可能过期的状态，天然覆盖"改了但最终下架""SKU 全部禁用"等交叉场景；乱序仲裁交给 search 端基于 updatedAt 的 external_gte，而非事件内标志。
- 影响评估: 每次同步多一次单条投影查询（同库本地 SQL，成本低）；操作语义不可从事件表追溯（M5 事件不落库）。

### DEV-2

- 原 DU 建议: `SearchSyncClient` 使用 OpenFeign（超时 1s/3s）。
- 实际实现: Spring `RestClient`，connect 1s / read 3s 与建议一致，`X-Internal-Token` 直连 8107。
- 原因: 与仓内 `SkuClient`、`ProductProjectionClient` 保持同一内部调用范式，M5 仅两端点。
- 影响评估: 无功能差异。

### DEV-3

- 原 DU 建议: 7 个写方法发布点。
- 实际实现: 8 个——create/update/changeStatus/addSku/updateSku/changeSkuStatus/publish/unpublish 全部发布事件（`CHANGE_STATUS` 与 `CHANGE_SKU_STATUS` 各自独立）。
- 原因: 与 requirement-design 列明的写操作集合对齐（设计现状描述即 8 个事务方法）；配合 DEV-1 重查策略，多发一次事件无副作用（幂等+版本仲裁）。
- 影响评估: 无；事件量略多于建议值，监听器与消费端完全幂等。

### DEV-4（说明项，非偏离）

- product→search 采用"AFTER_COMMIT 应用事件 + 同步 HTTP 投递"而非 MQ：此为 requirement-design §关键决策表明示采纳方案（「直接 MQ：M7 才引入，需求明确不提前」），不属于实现偏离；search 不可达由消费端落表补偿（DU-BE-506）与一致性检查（DU-BE-504）兜底，"product 完全调不通 search 的丢失窗口"为 M5 已接受边界。

## 自检

### 任务清单

| 任务 | 证据 | 结果 |
| --- | --- | --- |
| 任务 1 事件与发布点（op 映射） | `ProductSearchChangedEvent` + 8 个发布点；op 判定移至监听器重查（见 DEV-1/DEV-3） | ✅ |
| 任务 2 AFTER_COMMIT 监听器（回滚/提交/吞异常） | `createPublishUnpublishEventChain`（提交后调用）、`listenerFailureNeverBreaksWriteApi`（异常吞咽）；回滚零调用无独立用例（依赖 `@TransactionalEventListener` 框架语义） | ⚠️ 提交/异常已覆盖；回滚矩阵未自动化 |
| 任务 3 SearchSyncClient（1s/3s）与无启用 SKU→delete | `SearchSyncClient`（1s/3s）；无启用 SKU 查无投影→delete 由 `singleProjectionAndNotFoundCases` + 事件链共同覆盖；超时值无断言测试 | ✅（超时配置代码可评审，TC-009 无自动化） |
| 任务 4 内部 upsert/delete 端点（SERVICE/200/幂等） | `syncUpsertAcceptedAndSearchable`、`deleteEndpointIdempotent`、`authGuards`（401） | ✅ |
| 任务 5 跨服务联调（上架/改名/改价/下架） | 双端均有方法级测试；真实双进程 + ES 轮询 5s 的端到端联调未自动化，留 Integration Gate | ⚠️ 方法级齐，E2E 留 Integration Gate |

### Acceptance Criteria

| AC | 证据 | 结果 |
| --- | --- | --- |
| AC-001 上架提交后 5s 内 ES 可检出 | product 侧 `createPublishUnpublishEventChain`（提交后 sync 带 ON_SALE 投影）+ search 侧 `syncUpsertAcceptedAndSearchable`（受理即 refresh 可搜）；"5s"端到端时延未跨进程实测 | passed（双端方法级；时延 E2E 留 Integration Gate） |
| AC-002 改名/分类品牌/换图/改价后字段刷新 | 投影 `projectionPageOnSaleOnly` 字段断言 + 事件链 publish 投影（minPrice 8800）+ `syncUpsertAcceptedAndSearchable` 文档可搜；变更后重发事件覆盖同 docId | passed（方法级；逐字段变更 E2E 未自动化） |
| AC-003 下架硬删除；重复下架/删除不存在 id 成功 | `createPublishUnpublishEventChain`（unpublish→delete）、`offSaleSyncDeletesAndStaleDeleteKeepsNew`、`deleteEndpointIdempotent`（重复/缺文档 200） | passed（`deleteEndpointIdempotent`、`offSaleSyncDeletesAndStaleDeleteKeepsNew`） |
| AC-004 SKU 极值正确；无启用 SKU 不产生文档 | 投影 SQL MIN/MAX（INNER JOIN ENABLED SKU）+ `projectionPageOnSaleOnly`（1000/3000）；查无投影监听器走 delete（事件链草稿 CREATE→delete） | passed（`projectionPageOnSaleOnly`、事件链） |
| AC-005 search 停摆时写操作仍成功不回滚 | `listenerFailureNeverBreaksWriteApi`（client 抛异常，publish 200）；消费端故障落表 200 见 `acceptedOnEsFailureAndRecorded`（DU-BE-506） | passed（`listenerFailureNeverBreaksWriteApi`） |
| AC-006 事务回滚零同步调用 | 监听器为 `@TransactionalEventListener(AFTER_COMMIT)`（回滚不触发由框架保证）；测试覆盖提交路径，无显式回滚用例 | ⚠️ 框架语义保证，缺回滚专项用例 |
| AC-007 内部端点需 SERVICE、经网关 404 | `authGuards`（内部端点无凭证 401）、`projectionWithoutTokenUnauthorized`；gateway `/api/internal/**` denyAll→404 配置保证，未 E2E | passed（401 自动化；网关 404 配置保证） |

### Verification 对照

- Unit（AFTER_COMMIT 回滚/提交矩阵、7 方法 op 断言、异常吞咽）：提交链与异常吞咽已覆盖（2 例）；回滚矩阵与逐发布点 op 断言未编写（op 判定已移至重查，见 DEV-1）——⚠️。
- Integration（双模块 + ES Testcontainers 联调；停 mall-search 写 200）：消费侧真实 ES IT 与生产侧监听器测试分别就位；双进程联调留 Integration Gate——⚠️。
- API（内部端点 401/403/200 accepted、删除缺文档 200、网关 404）：401/200/幂等删除均自动化；403 由 `authGuards` 覆盖；网关 404 配置保证——✅（除网关外）。
- Migration: N/A（V1 由 DU-BE-503 建表）——N/A。
- Error Case（search 不可达、读超时、回滚）：search 不可达有 `listenerFailureNeverBreaksWriteApi`；RestClient 1s/3s 读超时无注入测试；回滚见 AC-006——⚠️ 部分。
