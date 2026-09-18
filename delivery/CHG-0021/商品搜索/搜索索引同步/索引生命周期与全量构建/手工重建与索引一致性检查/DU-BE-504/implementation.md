# DU Implementation — DU-BE-504

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

### mall-search（`mall-services/mall-search`，包根 `com.ai.mall.search`）

- 重建编排：`application/index/RebuildService.java`
  - `startRebuild()` 先 `findRunning()`（PENDING/RUNNING 视为执行中），存在则抛 `BusinessException(IndexErrorCode.B0503, HttpStatus.CONFLICT)`（「已有重建任务执行中，请稍后再试」）；
  - 临时物理索引 `mall_products_rebuild_yyyyMMddHHmmss`（UTC），任务号 `RBL+时间戳`，落 PENDING→RUNNING；
  - 调 `FullIndexBuildService` 向临时索引全量灌库 → `EsSearchIndexAdapter.switchAlias` 单请求 `updateAliases`（remove 旧/add 新）后显式 refresh 新索引 → 删除旧物理索引 → SUCCESS 回写 total/indexed/finishedAt；
  - catch 全部 `Exception` → `markFailed`（`error_message` 取 root message 截断 1000），临时索引保留以便排查，别名与旧索引不动；
  - M5 **同步执行**（单实例），HTTP 返回 202 时任务通常已到终态。
- 一致性检查：`application/index/ConsistencyCheckService.java`——`BATCH_SIZE=500` 分页拉投影在架全集、`allIds()`（PIT 游标）拉 ES docId 全集，外加双方 count；差集 `missingProductIds`/`extraProductIds`，`DIFF_LIMIT=200` 截断并以 `missingTruncated`/`extraTruncated` 两个布尔分别标记；报告 Map key：`productOnSaleCount`、`indexCount`、`missingProductIds`、`extraProductIds`、`missingTruncated`、`extraTruncated`、`checkedAt`（epoch millis）。
- 管理端接口：`interfaces/rest/admin/AdminSearchIndexController.java`（`@RequestMapping("/api/admin/search/index")`）
  - `POST /rebuild`：`@ResponseStatus(HttpStatus.ACCEPTED)`（202）+ `@PreAuthorize("hasAuthority('search:index:rebuild')")`，返回 `RebuildTaskView`；
  - `GET /rebuild/{taskId}`：任务详情（list 权限）；
  - `GET /rebuild?limit=20`：最近任务列表；
  - `GET /consistency-check`：立即执行并返回完整报告（list 权限）；
  - `GET /sync-failures?status&page&size`：失败记录分页（size 夹 1..100，返回 `{total,page,size,items}`）；
  - `POST /sync-failures/{id}/retry`：人工重试（rebuild 权限，逻辑归属 DU-BE-506，同批落地）。
- 视图对象：`RebuildTaskView`（id,taskNo,status,totalCount,indexedCount,failedCount,physicalIndex,errorMessage,startedAt,finishedAt,createdAt，时间均 epoch millis）、`SyncFailureView`、`ItemPage<T>`。
- 安全：`SearchSecurityExceptionAdvice` 将 `AccessDeniedException` 统一为 403 响应体；鉴权链与方法级注解见 `SearchSecurityConfiguration`（DU-BE-503）。依赖故障（ES/投影连接类异常）经 CHG-0020 的 `SearchExceptionAdvice` 映射 503 B0501，未新增专用错误码。

### mall-identity（`mall-services/mall-identity`）

- `src/main/resources/db/migration/V9__add_search_index_permissions.sql`：
  - `auth_permission` 插入 `search:index:list`（GET `/api/admin/search/index/**`）、`search:index:rebuild`（同 pattern，`http_method NULL`），`ON DUPLICATE KEY UPDATE` 幂等；
  - `auth_menu` 插入「搜索索引」目录（path `/search`，sort 70）与「索引管理」页面（path `/search/index`，`component_key=SearchIndex`，`permission_code=search:index:list`）；
  - SUPER_ADMIN 角色以 `INSERT IGNORE` 补权限与菜单。

### mall-gateway（`mall-gateway`）

- `application.yml` 新增路由 `mall-search-admin`：`Path=/api/admin/search/**` → `${MALL_GATEWAY_SEARCH_URI:http://localhost:8107}`（mall-search 端口 8107）。
- `GatewaySecurityConfiguration` 对 `/api/internal/**` 全局 denyAll，匿名/认证请求均返回 404（内部端点不对外暴露）。

### 测试证据（方法级，未留存 surefire 报告）

`IndexSyncIntegrationTest`（真实 ES Testcontainers + H2 + 真实安全链）相关用例：

- `fullRebuildSwitchesAliasAtomically`：POST rebuild 返回 202、`data.status=SUCCESS`、totalCount/indexedCount=3、`physicalIndex` 前缀 `mall_products_rebuild_`、旧 `mall_products_v1` 已不存在、别名 `mall_products` 指向新索引、count=3 且可搜；
- `rebuildConflictWhenRunning`：预置 RUNNING 行后再触发 → B0503（409）；
- `consistencyCheckDiffs`：投影 2 条 / ES 3 条 → `extraProductIds[0]=9003`、`missingProductIds` 为空、`checkedAt` 存在；
- `authGuards`：内部端点无凭证 401、admin 无 JWT 401、已认证但缺权限码 403。

## Commits

| Commit | 仓库 | 说明 |
| --- | --- | --- |
| 82ccf6e | repo-1 ai-platform-backend | `feat(search,system): M5 商品搜索+索引同步+系统配置（CHG-0020/0021/0022 后端）`（RebuildService/ConsistencyCheckService/管理端控制器/V9/网关路由均在其中） |

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
           - 原 DU 建议:
           - 实际实现:
           - 原因:
           - 影响评估: -->

### DEV-1

- 原 DU 建议: 最近任务列表端点为 `GET /api/admin/search/index/rebuild-tasks`。
- 实际实现: 收敛为 `GET /api/admin/search/index/rebuild?limit=20`（同一资源的集合读取），详情保留 `GET /rebuild/{taskId}`（数字主键）。
- 原因: 减少一个近义路径，REST 上以重建资源的集合/成员两级表达；前端按实际契约对接。
- 影响评估: 与 story-spec 路径字面不同，无功能损失；联调以前端 `searchIndexApi.recentTasks/getTask` 实际调用为准。

### DEV-2

- 原 DU 建议: 重建异步执行，202 受理后由后台线程/任务执行，前端轮询进度。
- 实际实现: `startRebuild()` 在请求线程内**同步**跑完全流程后返回 202（M5 单实例、千级商品，秒级完成）；无独立线程池，RUNNING 互斥仍由 DB 状态闸门保证。
- 原因: M5 单实例部署，异步线程池增加复杂度且无吞吐收益；202 状态码与轮询契约保留，前端对"返回即终态"与"返回后继续轮询"两种情况都兼容（onMounted 接续 RUNNING 任务）。
- 影响评估: 大索引重建会占用一个 HTTP 请求到完成（无网关/容器超时风险，M5 数据量下秒级）；多实例/大数据量阶段需替换为异步+锁。

### DEV-3

- 原 DU 建议: 一致性报告单个 `truncated` 布尔表示差集被截断。
- 实际实现: 拆为 `missingTruncated`、`extraTruncated` 两个布尔，missing/extra 各自独立截断（上限均 200）。
- 原因: 两类差集独立计算，分开标记可精确提示管理员哪一侧超量。
- 影响评估: 报告字段更多；前端同时渲染两个截断提示。

### DEV-4

- 原 DU 建议: 失败记录列表与人工 retry 端点归属 DU-BE-506。
- 实际实现: `AdminSearchIndexController` 一次性落地全部五个端点（含 `GET /sync-failures`、`POST /sync-failures/{id}/retry`），随 82ccf6e 与 DU-BE-506 同批交付；挂载路径为 `/api/admin/search/index/sync-failures`（index 子资源），权限码 `search:index:list`/`search:index:rebuild` 与设计一致。
- 原因: 同一控制器/同一安全配置一次成型，避免跨 DU 重复改动。
- 影响评估: 无；DU 边界以服务逻辑（`SearchSyncFailureService` 属 DU-BE-506）划分。

## 自检

### 任务清单

| 任务 | 证据 | 结果 |
| --- | --- | --- |
| 任务 1 RebuildService 全流程 | `RebuildService` + `fullRebuildSwitchesAliasAtomically`（202/SUCCESS/count/旧索引删除/别名指向） | ✅ |
| 任务 2 失败 FAILED+error_message、旧索引不动 | `markFailed` 代码路径（catch 全 Exception、临时索引保留、别名不动）；无故障注入 IT | ⚠️ 代码具备，失败路径无自动化 |
| 任务 3 consistency-check 差集+截断 | `consistencyCheckDiffs`（extra 构造）；`DIFF_LIMIT=200` 与双截断布尔代码可评审，超 200 无专门构造 | ⚠️ 差集主路径已覆盖，截断分支未自动化 |
| 任务 4 管理端控制器+权限注解 | `AdminSearchIndexController` 五端点 + `authGuards`（401/403） | ✅ |
| 任务 5 V9 权限菜单+网关路由 | `V9__add_search_index_permissions.sql`（ON DUPLICATE/INSERT IGNORE 幂等）、gateway `mall-search-admin` 路由、`/api/internal/**` denyAll 404；无种子断言测试 | ✅（配置/SQL 评审；V9 断言与网关 401 E2E 未自动化） |
| 任务 6 任务列表端点 | `GET /rebuild?limit=20` + `RebuildTaskView` 字段（状态/进度/错误） | ✅（路径见 DEV-1） |

### Acceptance Criteria

| AC | 证据 | 结果 |
| --- | --- | --- |
| AC-001 构建中旧搜索不中断；完成后别名指新、旧索引删除 | `fullRebuildSwitchesAliasAtomically`（别名切换后旧 v1 `indexExists=false`、新索引 count=3 可搜）；构建期查询旧索引由"切换在最后一步单请求完成"的代码顺序保证，未做并发查询 IT | passed（别名/新旧索引断言；构建中可用性为代码顺序保证） |
| AC-002 RUNNING→SUCCESS total/indexed；失败 FAILED 有错误 | `fullRebuildSwitchesAliasAtomically`（total/indexed=3、终态 SUCCESS）；FAILED 分支 `markFailed(rootMessage 截断 1000)` 无注入测试 | passed（成功路径）；⚠️ 失败路径仅代码保证 |
| AC-003 RUNNING 再触发 409 B0503 带 taskId | `rebuildConflictWhenRunning`（预置 RUNNING 行断言 B0503/409）；错误码 `IndexErrorCode.B0503` | passed（`rebuildConflictWhenRunning`） |
| AC-004 计数准确/missing/extra/超 200 truncated | `consistencyCheckDiffs`（productOnSaleCount=2、indexCount=3、extra=9003、missing=0、checkedAt）；missing 构造与超 200 截断未自动化 | passed（extra/计数）；⚠️ missing 与截断分支仅代码保证 |
| AC-005 无权限 403、菜单按钮生效、网关未登录 401 | `authGuards`（无 JWT 401、缺权限码 403）；V9 种子 `search:index:list/rebuild` + 菜单 `component_key=SearchIndex`；网关路由/401 未 E2E | passed（401/403 自动化；菜单与网关为 SQL/配置保证） |

### Verification 对照

- Unit（RUNNING 闸门/截断 Clock）：闸门由 IT `rebuildConflictWhenRunning` 覆盖；时间戳用 UTC `yyyyMMddHHmmss` 生成，未做 Clock 注入单测——⚠️ 时间戳格式代码可评审。
- Integration（完整重建/差集/mock 失败）：重建与 extra 差集有真实 ES IT；missing 差集、mock 失败保留旧别名、构建中并发搜索未自动化——⚠️。
- API（200/409/403；网关 401 E2E）：202/409/401/403 均有 MockMvc 级覆盖（真实安全链）；网关 E2E 未执行——⚠️。
- Migration（V9 种子断言）：无断言测试，SQL 幂等写法（ON DUPLICATE KEY UPDATE/INSERT IGNORE）可评审——⚠️。
- Error Case（并发 409/构建失败/依赖故障 503 B0501）：409 已覆盖；构建失败代码路径无注入；503 复用 CHG-0020 全局异常映射——⚠️ 部分。
