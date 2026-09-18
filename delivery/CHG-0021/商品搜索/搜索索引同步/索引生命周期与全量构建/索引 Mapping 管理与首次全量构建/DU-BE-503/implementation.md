# DU Implementation — DU-BE-503

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

### mall-search（`mall-services/mall-search`，包根 `com.ai.mall.search`）

- Flyway：`src/main/resources/db/migration/V1__create_search_sync_tables.sql` 建 `search_index_rebuild_task`（`task_no` 唯一键、`idx_rebuild_status_created`）与 `search_sync_failure_record`（`idx_sync_failure_status_next`、`idx_sync_failure_product`，`max_retries default 5`）两表；`max_retries`、`failed_count` 等列与 MySQL 8 / H2 `MODE=MySQL` 兼容。
- Mapping 资源：`src/main/resources/es/product-index.json`（冻结版，IK：`name.ik` ik_max_word/ik_smart，`productId long`、`mainImage keyword index:false`、`minPrice/maxPrice long`、`publishedAt/updatedAt date epoch_millis`，1 shard/0 replica）与 `es/product-index-standard.json`（字段类型完全一致，仅分词器回退 standard）。
- `infrastructure/elasticsearch/SearchIndexLifecycleManager.java`：`ApplicationRunner` + `@Order(HIGHEST_PRECEDENCE)`，启动执行 `ensureIndex()`——物理索引 `mall_products_v1` 不存在则创建、别名 `mall_products` 缺失则挂接，幂等；ES 不可达仅 `log.error` 不阻断应用启动；物理索引与别名同名时跳过挂别名。
- `infrastructure/elasticsearch/EsSearchIndexAdapter.java`（implements `domain/index/SearchIndexPort`，承接 CHG-0020）：
  - `createProductIndex` 先加载 IK 版 JSON，检测到 IK 插件缺失异常（分词器/分析器含 `ik`）时回退 standard 版；
  - `bulkUpsert` 以 `updatedAt` epoch millis 走 `ExternalGte` 外部版本、`refresh=false`，`response.hasErrors()` 时抛 `IllegalStateException`（首条 id:reason）；
  - `count()`、`allIds()` 采用 PIT + `_shard_doc` `search_after` 游标（ES 8 禁止按 `_id` 排序），500/批。
- 全量构建：`application/index/FullIndexBuildService.java`（`PAGE_SIZE=500`，页号从 1 起，逐页拉投影 bulk，`ProgressCallback` 回写 total/indexed）。
- 领域与持久化：`domain/index` 下 `SearchIndexPort`、`IndexRebuildTask`、`RebuildStatus`（PENDING/RUNNING/SUCCESS/FAILED）、`RebuildTaskRepository`；`infrastructure/persistence` 下 MyBatis-Plus Po/Mapper/Repository 实现；`infrastructure/config/MybatisPlusConfig.java`。
- 投影拉取：`application/index/projection/ProductProjectionView.java`（13 字段）、`ProjectionPage.java`；`infrastructure/client/ProductProjectionClient.java` 使用 Spring `RestClient`（connect 2s / read 5s，`X-Internal-Token`，base `mall.sync.product-uri:http://localhost:8103`），解包 `UnifyResult`，单条端点 404 归一为 `null`。
- 安全：`infrastructure/config/SearchSecurityConfiguration.java`（`@Profile("!test")`；`/api/mall/** permitAll`、`/api/internal/** hasRole SERVICE`、`/api/admin/** hasRole ADMIN`，`@EnableMethodSecurity`，自定义 401/403 响应体）。
- 健康：`infrastructure/health/SearchHealthIndicator.java`（ES ping 失败 DOWN，暴露于 `/actuator/health` 的 `components.search`）。
- 配置：`src/main/resources/application.yml` 增加 `mall.search.*`（别名/索引名、同步重试延迟等）与 `mall.sync.product-uri`；test profile 使用 H2 内存库并启用 Flyway（真实 ES 由 Testcontainers 8.17.4 提供）。

### mall-product（`mall-services/mall-product`，包根 `com.ai.mall.product`）

- 投影查询：`infrastructure/persistence/product/ProductSearchProjectionMapper.java`，`@Select` 注解 SQL——以 `product_sku status='ENABLED' AND deleted=0` 派生表 `INNER JOIN` 分组取 `MIN/MAX sale_price`，`LEFT JOIN` 分类/品牌名，`WHERE p.status='ON_SALE' AND p.deleted=0`，分页 `ORDER BY p.id ASC`；单条查询加 `AND p.id=#{}`；`keywords` 固定 `'' AS keywords`（M5 预留）。
- 投影装配：`ProductSearchProjectionPo`、`application/search/ProductSearchProjectionService.java`（分页/单条）、`application/search/SearchProjectionView.java`（13 字段，与 mall-search 端同名同序）、`SearchProjectionPage(items,total,page,size)`。
- 内部端点：`interfaces/rest/internal/InternalProductController.java`
  - `GET /api/internal/products/search-projection?page=1&size=500`；
  - `GET /api/internal/products/{productId}/search-projection`：不存在 / 非在架 / 无启用 SKU 均抛 `ProductException.notFound` → HTTP 404（mall-search 客户端归一为 null）。

### 测试证据（方法级，未留存 surefire 报告）

- mall-search `src/test/.../index/IndexSyncIntegrationTest.java`（真实 ES Testcontainers + H2 + 真实安全链）：
  - `ensureIndexIdempotentWithStandardFallback`（TC-001/002）：建索引+别名、重复调用幂等、standard 回退；
  - `fullRebuildSwitchesAliasAtomically` 中断言构建后 ES `count=3` 与抽样文档可搜（TC-005 全量口径旁证）。
- mall-product `src/test/.../interfaces/rest/internal/ProductSearchProjectionApiTest.java`（4 例）：
  - `projectionPageOnSaleOnly`（2 条、total、字段、价区 1000/3000）、`paginationOrderAndSize`（id 升序翻页）、`projectionWithoutTokenUnauthorized`（401）、`singleProjectionAndNotFoundCases`（5001 200；无启用 SKU/草稿/不存在均 404）。

## Commits

| Commit | 仓库 | 说明 |
| --- | --- | --- |
| 82ccf6e | repo-1 ai-platform-backend | `feat(search,system): M5 商品搜索+索引同步+系统配置（CHG-0020/0021/0022 后端）`（M5 三 Change 合并提交，DU-BE-503 全部内容在其中） |

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
           - 原 DU 建议:
           - 实际实现:
           - 原因:
           - 影响评估: -->

### DEV-1

- 原 DU 建议: `ProductProjectionClient` 使用 OpenFeign（name=mall-product）拉取投影。
- 实际实现: 使用 Spring `RestClient` 直连 `http://localhost:8103`，手工携带 `X-Internal-Token` 并解包 `UnifyResult`；超时 connect 2s / read 5s。
- 原因: M5 仅两个 GET 端点，仓内既有内部客户端（`SkuClient` 及本 Change 的 `SearchSyncClient`）均为 RestClient 范式，避免为单用途引入 Feign 编解码/服务发现配置。
- 影响评估: 契约等价；read 超时较 product→search 方向（3s）放宽为 5s，全量分页场景更稳。

### DEV-2

- 原 DU 建议: 提供内部触发端点 `POST /api/internal/search/index/full-build` 执行首次全量。
- 实际实现: 未新增该端点；首次全量复用 `RebuildService` 管理端重建链路（临时索引 `mall_products_rebuild_*` + 别名原子切换，见 DU-BE-504），启动期 `SearchIndexLifecycleManager` 仅保证空 `mall_products_v1` + 别名 `mall_products` 存在。
- 原因: 触发入口统一收敛到带 ADMIN 权限码的运维端点，避免新增无用户身份的内部触发面；重建链路本身包含完整全量灌库。
- 影响评估: 首次灌库需人工调用一次 `POST /api/admin/search/index/rebuild`；功能与可观测性（任务记录）更完整。

### DEV-3

- 原 DU 建议: 冻结 Mapping 固定使用 IK 分词器（`product-index.json`）。
- 实际实现: 保留 IK 版为首选；ES 镜像/Testcontainers 未安装 analysis-ik 时自动回退 `product-index-standard.json`，字段类型（long/keyword/date/index:false）完全一致，仅分词器不同。
- 原因: M5 基础镜像与 CI 不保证 IK 插件可用，回退保证全新环境可启动、可测试。
- 影响评估: 无 IK 环境中文分词退化为 standard，M5 搜索功能可用、分词质量后续随插件补齐即自动恢复（无需改代码）。

### DEV-4

- 原 DU 建议: 投影 SQL 以 `EXISTS` 子查询保证"≥1 启用 SKU"口径，内存装配价格极值；单条投影不存在时返回 `data=null`（200）。
- 实际实现: ① 采用启用 SKU 派生表 `INNER JOIN` 一次查询同时完成"存在启用 SKU"过滤与 `MIN/MAX(sale_price)` 极值；② 单条投影不存在/非在架/无启用 SKU 一律 HTTP 404（`ProductException.notFound`），由 `ProductProjectionClient` 归一为 null（requirement-design §2.1 明示 404 语义=已删除/不可见）。
- 原因: ① 口径等价且 total 与极值一次查询得到，避免内存过滤破坏分页 total；② 404 是 REST 更明确的不存在语义，且 search 侧重放逻辑只需 null/非 null 二分。
- 影响评估: 行为与 story-spec 的 `data=null` 描述有字面差异，消费端已兼容；投影无"下架但保留行"的中间态。

### DEV-5

- 原 DU 建议: `categoryName`/`brandName` 使用 text + keyword 子字段。
- 实际实现: 两份 mapping JSON 中仅 text（IK/standard），未建 keyword 子字段；精确筛选走 `categoryId`/`brandId`（long）。
- 原因: M5 无按分类/品牌名称精确匹配、排序或聚合的需求场景。
- 影响评估: 后续若需要名称精确匹配/聚合，需新增 mapping 版本（别名架构已支持）。

## 自检

### 任务清单

| 任务 | 证据 | 结果 |
| --- | --- | --- |
| 任务 1 V1 两表 | `V1__create_search_sync_tables.sql` 两表 + 唯一键/索引；H2 + Flyway 在所有 `@SpringBootTest` 中生效（测试直接 INSERT 两表成功） | ✅（列差异见 DU-BE-506 DEV-1） |
| 任务 2 mapping/settings | `es/product-index.json` + `product-index-standard.json`；建索引成功即 mapping 合法 | ✅（见 DEV-3/DEV-5） |
| 任务 3 ensureIndex 幂等 + ES 宕机不阻启动 | `ensureIndexIdempotentWithStandardFallback`；宕机不阻启动由 lifecycle `try/catch log.error` 代码保证，无专门宕机 IT | ⚠️ 幂等有自动化；宕机场景仅代码保证，建议补 IT |
| 任务 4 投影 Mapper/分页/单条 SERVICE | `projectionPageOnSaleOnly`、`paginationOrderAndSize`、`singleProjectionAndNotFoundCases`、`projectionWithoutTokenUnauthorized`；网关 404 由 gateway `denyAll` 配置保证（未 E2E） | ✅（404 语义见 DEV-4） |
| 任务 5 ProductProjectionClient | `ProductProjectionClient`（RestClient 2s/5s，404→null） | ✅（见 DEV-1） |
| 任务 6 分批 Bulk / 批次失败 FAILED | `FullIndexBuildService` 500/批 + `RebuildService.markFailed` 代码路径；无 1001 条/批次失败自动化用例 | ⚠️ 代码具备，1001 条与中途失败未自动化 |

### Acceptance Criteria

| AC | 证据 | 结果 |
| --- | --- | --- |
| AC-001 全新启动建 v1+别名、重启不改 | `ensureIndexIdempotentWithStandardFallback`（两次启动语义、别名断言） | passed（`ensureIndexIdempotentWithStandardFallback`） |
| AC-002 Mapping 可评审、类型符合冻结设计 | 两份 es JSON 静态评审 + standard 回退建索引成功；`mainImage index:false`、long/date 类型与冻结版一致 | passed（资源评审 + 建索引 IT） |
| AC-003 无令牌拒绝/网关 404/持令牌分页含 total | `projectionWithoutTokenUnauthorized`、`projectionPageOnSaleOnly`（total 断言）；网关对 `/api/internal/**` denyAll 且返 404（配置评审，未 E2E） | passed（401/分页自动化；网关 404 配置保证） |
| AC-004 排除下架/无启用 SKU、极值正确 | `projectionPageOnSaleOnly`（价区 1000/3000）、`singleProjectionAndNotFoundCases`（无启用 SKU/草稿 404） | passed（`projectionPageOnSaleOnly`、`singleProjectionAndNotFoundCases`） |
| AC-005 全量后 count=总数、抽样字段正确 | `fullRebuildSwitchesAliasAtomically`（count=3、physicalIndex、别名指向）+ `syncUpsertAcceptedAndSearchable`（抽样字段可搜） | passed（`fullRebuildSwitchesAliasAtomically`） |
| AC-006 1001 条分批成功；中途失败 FAILED 可定位 | `FullIndexBuildService` 500/批 + `RebuildService` 全量 catch→`markFailed(rootMessage 截断 1000)` 代码路径；无 1001 条数据构造与批次失败注入测试 | ⚠️ 未自动化，建议 Integration Gate 补大批量/故障注入用例 |
| AC-007 V1 在 mall_search 建两表 | `V1__create_search_sync_tables.sql`；集成测试经 Flyway 建表后直接操作两表 | passed（H2/Flyway 间接验证；未做列级迁移断言） |

### Verification 对照

- Unit（投影 Mapper/ensureIndex mock）：投影口径由 `ProductSearchProjectionApiTest` 覆盖；ensureIndex 走真实 ES IT 而非 mock——✅（方式升级）。
- Integration（ES Testcontainers + MySQL testcontainers）：真实 ES Testcontainers 已具备；关系库实际使用 H2（`MODE=MySQL`）+ Flyway，未使用 MySQL testcontainers——⚠️ MySQL 方言差异仅靠 SQL 兼容写法保证。
- API（SERVICE 401/403/200）：401 有自动化（`projectionWithoutTokenUnauthorized`）；200 由分页/单条用例覆盖；403 在 search 侧 `authGuards` 覆盖。
- Migration（V1 列/索引断言）：无独立迁移断言测试，靠集成测试建表使用间接覆盖——⚠️。
- Error Case（ES 宕机启动、投影 5xx、批次失败）：客户端/服务层异常路径代码具备（lifecycle catch、build 失败 markFailed），均无专门自动化——⚠️。
