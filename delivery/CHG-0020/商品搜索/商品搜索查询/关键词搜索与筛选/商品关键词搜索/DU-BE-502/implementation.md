# DU Implementation — DU-BE-502

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

### mall-services/mall-search：关键词查询主链路（domain → application → infrastructure → interfaces）

- `domain/search/SearchProductDocument.java`：索引读模型 record（productId/productName/keywords/categoryId/categoryName/brandId/brandName/mainImage/status/minPrice/maxPrice/publishedAt/updatedAt），常量 `STATUS_ON_SALE="ON_SALE"`；docId=productId，与 CHG-0021 写模型字段冻结一致。
- `domain/search/SearchPage.java`：`record SearchPage<T>(List<T> items, long total, int page, int size)`——响应分页契约。
- `domain/search/ProductSearchItem.java`：搜索结果**白名单摘要** record（productId/productName/mainImage/minPrice/maxPrice/brandName/categoryName，共 7 字段；金额整数分），不含 SKU 列表/keywords/status 等聚合与索引字段。
- `domain/search/SearchQuery.java`、`SortMode.java`：查询值对象（keyword/分页 + 预留筛选/排序槽位）；SortMode 四枚举 DEFAULT/PRICE_ASC/PRICE_DESC/NEWEST，`from(String)` 未知值安全回退 DEFAULT。
- `domain/search/ProductSearchPort.java`：出站端口 `SearchPage<ProductSearchItem> search(SearchQuery)`。
- `application/search/ProductSearchService.java`：参数归一——keyword trim（空白→null）、长度 ≤64（超长 IllegalArgumentException→B0502）、page null/<1 → 1、size null/<1 → 20、size>100 截断 100；价格非负/min≤max 校验（与 DU-BE-511 同批落地）；categoryId/brandId 非正忽略。
- `infrastructure/elasticsearch/ElasticsearchProductSearchAdapter.java`（implements ProductSearchPort，索引名 `@Value("${mall.search.index-alias:mall_products}")`）：
  - bool.must：keyword 非空时追加 `multi_match(query, fields=["productName^3","keywords","brandName","categoryName"], operator=or)`；keyword 为空（浏览态）不加 must；
  - filter 恒含 term `status=ON_SALE`；
  - DEFAULT 排序 `_score desc, updatedAt desc`；from=(page-1)*size、size、`trackTotalHits=true`；
  - hits → SearchProductDocument → ProductSearchItem 仅映射白名单字段；`hits.total` 空安全；IOException 包 UncheckedIOException 上交 Advice。
- `interfaces/rest/mall/MallSearchController.java`：`GET /api/mall/search/products`（@RequestParam keyword/categoryId/brandId/minPriceFen/maxPriceFen/sort/page/size，全部 required=false），UnifyResult 包裹；无身份入参、无任何写端点。
- `infrastructure/config/SearchSecurityConfiguration.java`（@Profile("!test")）：资源链 `/actuator/health`、`/api/mall/**` permitAll（本 DU 的公开访问双保险；同文件的 /api/internal SERVICE、/api/admin ADMIN 链为 CHG-0021 同合并提交带入）；测试以 `support/SearchApiTestSecurityConfig.java` 等价链装配。

### mall-gateway：匿名搜索路由

- `mall-gateway/src/main/resources/application.yml`（第 71–75 行）：路由 id `mall-search-mall`，`Path=/api/mall/search/**` → `${MALL_GATEWAY_SEARCH_URI:http://localhost:8107}`；不新增 /api/internal 搜索路由。
- `mall-gateway/src/main/java/com/ai/mall/gateway/security/GatewaySecurityConfiguration.java`（第 49–50 行）：匿名白名单追加 `/api/mall/search/**`；`/api/internal/**` 仍 denyAll→404。

### 测试

- `src/test/java/com/ai/mall/search/api/ProductSearchApiTest.java`（@SpringBootTest + MockMvc + 真实 ES 8.17.4，测试索引 `mall_products_it_search` 自建 mapping，bulk 105 条文档：精选 6 条含 OFF_SALE/无品牌/无图/无 publishedAt + 100 条种子），本 DU 相关用例：
  - `keyword_relevance_andOnSaleOnly`：keyboard 命中 productName(doc1) 与 keywords(doc2) 共 2 条且名称加权 doc1 置顶；「机械」仅命中 doc3，OFF_SALE doc4 任何查询不返回；
  - `responseWhitelist`：body 含 productId/productName/mainImage/minPrice/maxPrice/brandName/categoryName，不含 keywords/status/publishedAt/updatedAt；
  - `pagination`：默认 size=20、total=105；size=999 实际 size=100 且尾页 5 条；page=0 回显 page=1；
  - `missingIndex_throws`：适配器直查不存在的别名抛异常（交 Advice 归 503，回归 B05xx 口径）。
- 网关匿名 200 / internal 404 为 Integration Gate E2E 项（test-design §3 已标注网关弱单测，以配置核对 + 联测双保险）。

## Commits

| Commit | DU | 说明 |
| --- | --- | --- |
| 82ccf6e | DU-BE-502 | repo-1（M5 三 Change 合并提交）：mall-search 关键词 multi_match 查询/ON_SALE 硬过滤/白名单摘要/分页 + mall-gateway /api/mall/search/** 匿名路由 |

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
               - 原 DU 建议:
               - 实际实现:
               - 原因:
               - 影响评估: -->

### DEV-1
- 原 DU 建议: test-design TC-009 设想「keyword 长度 65、**size=0**、**page=abc** 绑定失败 → 400 B0502」；story-design §2 亦称「非法分页（page<1/size 越界/keyword 超长）：B0502 400」。
- 实际实现: size 走**静默归一**——null/<1（含 0）回退默认 20、>100 截断 100（ProductSearchService 第 57–62 行）；page<1（含 0/-1）回退第 1 页；仅 keyword>64、价格非法抛 IllegalArgumentException→400 B0502。page=abc 等非数字绑定抛 MethodArgumentTypeMismatchException，不属于 IllegalArgumentException，未在 SearchExceptionAdvice 映射 B0502，走 common-web 兜底。ProductSearchApiTest 实际断言：size=999→100、page=0→1（200），invalidParams 只覆盖价格反向/负数/keyword 65 长度。
- 原因: story-spec AC-004（更高优先级产品规格）冻结的是「size=500 被限制为 100；page=0/-1 回退第 1 页」的宽松口径，实现按 spec 而非 test-design 的 400 设想；非数字绑定属框架层类型错误，与业务参数非法语义不同。
- 影响评估: 前端与调用方对越界分页获得确定性 200 结果而非错误，浏览体验更稳；page=abc 不会得到 B0502 结构（返回 common-web 兜底错误），前端按非 2xx 进 Error 态，不影响安全；建议后续 Change 如需统一可补类型绑定异常映射。

### DEV-2
- 原 DU 建议: story-design §2 冻结 ProductSearchItem 9 字段：productId/productName/mainImage/minPrice/maxPrice/**categoryId/brandId**/categoryName/brandName。
- 实际实现: `ProductSearchItem` 收紧为 7 字段（无 categoryId/brandId）；两个 ID 仅存在于 SearchProductDocument 读模型，供 term 过滤使用、不下发响应。
- 原因: story-spec §4 对外契约只要求「productId/name/image/priceFen/brandName/categoryName」摘要集；结果卡片不消费类目/品牌 ID，按白名单最小下发原则收紧，与 test-design TC-003「字段白名单断言」意图一致。
- 影响评估: 响应体更小且无内部 ID 泄漏；前端 api/search.ts 类型同步为 7 字段；过滤能力（入参 categoryId/brandId）不受影响。

### DEV-3
- 原 DU 建议: requirement-design §2.1 拟名 `SearchRepository` / `ElasticsearchSearchRepository`；独立 `SearchSecurityConfiguration` 仅配置 mall permitAll。
- 实际实现: 出站端命名为 `ProductSearchPort` + `ElasticsearchProductSearchAdapter`（与既有服务 Adapter 命名同构）；SearchSecurityConfiguration 为合并提交完整形态（mall permitAll + CHG-0021 的 internal/admin 链），test profile 不装配、由 SearchApiTestSecurityConfig 等价替代。
- 原因: 与 mall-cart/mall-order 端口+适配器命名范式保持一致；安全链一次建成避免 CHG-0021 重复改造。
- 影响评估: `/api/mall/**` permitAll 与网关白名单双保险不变；命名差异不影响契约。

### DEV-4（跨 Change 同合并提交说明，非本 DU 范围）
- 原 DU 建议: 本 DU 查询入口仅参数归一后出站查询。
- 实际实现: `ProductSearchService.search` 首行含 `featureGate.ensureEnabled("search.enabled")`（mall-common-config FeatureGate，代码注释标 CHG-0022）；同合并提交带入 SearchFeatureGateTest（search.enabled 显式 false → 403 B0606）。
- 原因: M5 三 Change（CHG-0020/21/22）合并于同一提交 82ccf6e，开关切点落在本 DU 的服务方法上。
- 影响评估: FeatureGate 语义为 fail-open（缺键/读取失败默认放行，仅显式 false 拒绝），不改变 CHG-0020 无开关时的行为；本 DU 的 AC 与测试不依赖开关状态。

## 自检

对照 task-spec.md Verification：

- ✅ AC-001：keyword_relevance_andOnSaleOnly——productName/keywords 命中实证（multi_match 字段表含 productName^3/keywords/brandName/categoryName，brand/category 同算子覆盖），名称加权置顶。
- ✅ AC-002：同用例「机械」查询 OFF_SALE doc4 被剔除；适配器 filter 恒带 term status=ON_SALE（第 51–52 行）。
- ✅ AC-003：responseWhitelist 正/反字段断言；ProductSearchItem 7 字段白名单 record。
- ✅ AC-004：pagination——默认 20/上限 100/total=105/page<1 回退（边界处理见 DEV-1）。
- ✅ AC-005：pagination 无 keyword 请求 total=105 条 ON_SALE 浏览态结果（must 不追加、filter 仍生效）。
- ✅ AC-006：网关白名单 `/api/mall/search/**` permitAll（GatewaySecurityConfiguration 第 49–50 行）+ mall-search-mall 路由（application.yml 第 71–75 行）；匿名 200 联测归 Integration Gate。
- ✅ AC-007：网关无 /api/internal/search 路由且 `/api/internal/**` denyAll→404；mall-search 仅 MallSearchController 一个 GET 商城端点，无可达写端点。
- ✅ AC-008：missingIndex_throws + SearchExceptionAdviceTest 404/连接/超时三例统一 503 B0501。
- ✅ Unit：参数归一逻辑（trim/默认/上限/超长拒绝）由 ProductSearchApiTest invalidParams/pagination 经真实链路覆盖。
- ✅ Error Case：ES 停服/缺别名 → B0501（DU-BE-510 Advice）；越界分页静默归一。
- ✅ Migration：N/A。
- 说明：target/surefire-reports 不存在，用例数按源码枚举（ProductSearchApiTest 全类 9 例，本 DU 直接相关 4 例，其余属 DU-BE-511/510）；未杜撰执行数字。
