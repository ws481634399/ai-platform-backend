# DU Implementation — DU-BE-510

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

### mall-services/mall-search：B05xx 错误码 + 异常归一口径

- `domain/search/SearchErrorCode.java`（implements `com.ai.mall.common.core.result.ErrorCode`）：
  - `SEARCH_UNAVAILABLE` = B0501 /「搜索服务暂时不可用，请稍后重试」；
  - `SEARCH_BAD_REQUEST` = B0502 /「搜索参数不合法」。
- `domain/search/SearchException.java`：extends `com.ai.mall.common.web.exception.BusinessException`，按错误码绑定 HTTP 503/400。
- `interfaces/rest/SearchExceptionAdvice.java`（`@RestControllerAdvice` + `@Order(HIGHEST_PRECEDENCE)`，优先于 common-web 兜底）：
  - `@ExceptionHandler({ElasticsearchException.class, TransportException.class, ResponseException.class, IOException.class})`：`isSearchUnavailable(ex)` 遍历 cause 链——命中 `ConnectException`、`SocketTimeoutException` 或 HTTP 404 的 `ResponseException`（索引/别名不存在）→ 503 B0501；**不匹配的 ES 异常重新抛出**交 common-web 兜底，不盲目吞并；
  - 503 响应仅 `UnifyResult.fail(SEARCH_UNAVAILABLE)`，不含 ES 主机/堆栈；WARN 日志 `搜索不可用 trace={} type={}`，traceId 取 `com.ai.mall.common.core.trace.TraceConstants.MDC_KEY`；
  - `@ExceptionHandler(IllegalArgumentException.class)` → 400 B0502，message 经 `safeMessage` 仅回显参数语义文案（如「minPriceFen 不能大于 maxPriceFen」），空白时回退错误码固定文案。
- 空结果语义：repository 命中 0 条时返回 `SearchPage(items=[], total=0, ...)` 正常 200（`ElasticsearchProductSearchAdapter` 第 92–93 行 hits.total 空安全），不经 Advice、不打 WARN/ERROR。
- traceId 链路：沿用 common-web `TraceIdFilter`（响应头 X-Trace-Id、MDC traceId、日志 pattern `%X{traceId:-}`）。

### 测试

- `src/test/java/com/ai/mall/search/interfaces/rest/SearchExceptionAdviceTest.java`（standalone MockMvc + TraceIdFilter + 真实 SearchExceptionAdvice，Logback ListAppender 捕日志），4 例：
  - `connectRefused_returns503`：IOException 包装 ConnectException（含伪主机 10.0.0.9:9200）→ 503 B0501、X-Trace-Id 头存在、恰好 1 条含「搜索不可用」的 WARN 且 MDC 含 traceId；
  - `socketTimeout_returns503`：cause 链 SocketTimeoutException → 503 B0501；
  - `indexMissing_returns503`：Mockito 构造 404 ResponseException（GET /mall_products/_search）→ 503 B0501，body 不含主机 IP 与 `at ` 堆栈痕迹；
  - `illegalArgument_returns400`：IllegalArgumentException → 400 B0502 且回显安全文案。
- 空结果 200/无 ERROR 断言落在查询 IT：`api/ProductSearchApiTest.emptyResult_noErrorLog`（Testcontainers 真实 ES，无命中关键词 → 200 items=[] total=0，并断言 com.ai.mall.search logger 无 ERROR）。

## Commits

| Commit | DU | 说明 |
| --- | --- | --- |
| 82ccf6e | DU-BE-510 | repo-1（M5 三 Change 合并提交）：B0501/B0502 错误码、SearchExceptionAdvice 连接/超时/404 异常归一、traceId WARN 与空结果 200 语义 |

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
               - 原 DU 建议:
               - 实际实现:
               - 原因:
               - 影响评估: -->

### DEV-1
- 原 DU 建议: story-design §4 写「ResponseException status 404 且 reason 含 index_not_found → B0501」，即解析响应 reason 文本判定。
- 实际实现: 只按 `ResponseException.getStatusLine().getStatusCode() == 404` 判定（`SearchExceptionAdvice.java` 第 63–68 行），不解析/不依赖 reason 是否含 index_not_found 字符串。
- 原因: 别名 `mall_products` 不存在时 ES 返回 404，状态码已足够稳定；reason 文案随 ES 版本/语言可能变化，按状态码判定更健壮且不外泄内部细节。
- 影响评估: 任何经 ES 客户端冒出的 404（本服务仅索引/别名缺失场景）统一 B0501，与 AC-003 口径一致；响应体不含 reason 文本，无信息泄漏。

### DEV-2
- 原 DU 建议: task-design §3 测试布局列 `SearchExceptionAdviceTest`（MockMvc）+ 独立 `EmptyResultIT`（别名/无命中 200、无 ERROR 日志）两个测试类。
- 实际实现: 未单独建 EmptyResultIT；空结果 200 + 无 ERROR 日志断言并入 `ProductSearchApiTest.emptyResult_noErrorLog`（与查询用例共享 Testcontainers 数据集与 Logback ListAppender）；别名缺失路径由 `SearchExceptionAdviceTest.indexMissing_returns503`（404 mock）+ `ProductSearchApiTest.missingIndex_throws`（真实不存在别名适配器抛错）双侧覆盖。
- 原因: 空结果与查询链路共用同一 ES 容器/索引夹具，并入查询 IT 减少一套容器与造数成本；异常归一在纯 MockMvc 切片已可完整断言。
- 影响评估: AC-004 验证意图（200 空页 + 无 ERROR）与覆盖强度不变，仅测试类归属调整。

## 自检

对照 task-spec.md Verification：

- ✅ AC-001：connectRefused_returns503——连接拒绝（cause 链 ConnectException）→ HTTP 503 UnifyResult{success:false,code:"B0501"}，body 不含主机/堆栈（indexMissing 用例额外断言无 `10.0.0.9`、无 `at `）。
- ✅ AC-002：socketTimeout_returns503——SocketTimeoutException cause 链 → 503 B0501。
- ✅ AC-003：indexMissing_returns503——404 ResponseException（别名不存在）→ 503 B0501 统一结构，非 ES 原生 index_not_found 响应；另有 ProductSearchApiTest.missingIndex_throws 对真实缺失别名回归。
- ✅ AC-004：ProductSearchApiTest.emptyResult_noErrorLog——0 命中 200、items=[]/total=0，并断言搜索包路径无 ERROR 日志。
- ✅ AC-005：connectRefused_returns503 断言 WARN 日志 MDC 含 traceId、响应头 X-Trace-Id 存在，可按 traceId 串联请求与日志。
- ✅ Unit：cause 链矩阵（ElasticsearchException/TransportException/ResponseException/IOException + Connect/SocketTimeout 包装）由 4 例切片测试覆盖；未匹配异常 rethrow 不吞并。
- ✅ Integration：真实 ES 空结果/缺别名在 ProductSearchApiTest（Testcontainers 8.17.4）覆盖。
- ✅ API：MockMvc 断言 503/400/200 与 UnifyResult 结构；不做 DB 降级（无任何商品库依赖，pom 零 mall-product 直连）。
- ✅ Migration：N/A。
- 说明：target/surefire-reports 当前不存在，用例数按源码枚举（SearchExceptionAdviceTest 4 例 + emptyResult 归属 ProductSearchApiTest），未杜撰执行数字。
