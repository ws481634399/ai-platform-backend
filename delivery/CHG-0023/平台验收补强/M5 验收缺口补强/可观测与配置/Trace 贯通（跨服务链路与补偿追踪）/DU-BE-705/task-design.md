# DU Task Design — DU-BE-705

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-705 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-705 与同目录 task-spec.md（"做什么/验收"）配对互链。
> 本文件固定为 Expected Implementation（Plan / Sketch / Pseudocode），
> 与 implementation.md（Actual Implementation）分立，不得合并。

## 1. Goal

打通跨服务 traceId 出站传播与网关入站治理：common-web 新增 RestClient 拦截器并接入全部 13 个 internal client；网关以最高优先级响应式 WebFilter 实现生成/透传/全响应回写；合法性规则在 common-core 公共化供 servlet/reactive 两侧共用。零业务行为变更。

## 2. Repository

repo-1（implementation/ai-platform-backend）：mall-common/mall-common-core、mall-common/mall-common-web、mall-services/{mall-order,mall-cart,mall-product,mall-inventory,mall-member,mall-identity,mall-search}、mall-common/mall-common-config、mall-gateway。

## 3. Scope

- 新增：
  - mall-common-web：`trace/TraceClientHttpRequestInterceptor`（+ Test）
  - mall-gateway：`trace/TraceIdWebFilter`（+ Test）、pom 依赖 mall-common-core
- 修改：
  - mall-common-core：TraceContext（+isValid/Pattern 常量）
  - mall-common-web：TraceIdFilter（复用 isValid，删私有正则）
  - 13 个 internal client（见外部 story-design §1 清单）：builder 链各加一行 requestInterceptor
- 不动：UnifyResult/安全链业务语义/错误码/数据库/网关安全错误信封响应体/IdentityPropagationFilter。

## 4. Design References

- 外部 story-design.md §1（逐模块改动）、§2（接口契约）、§4（错误处理）、§6（TC-001~004/007 策略）
- 实证样板：
  - mall-common-web TraceIdFilter.java / TraceIdFilterTest.java（待复用的正则与 5 项行为）
  - mall-common-core TraceContext.java/TraceConstants.java
  - 13 client：mall-order infrastructure/client/RestInventoryPort.java（builder 样板，含 defaultHeader X-Internal-Token）
  - mall-gateway：CHG0015GatewaySecurityChainTest.java（WebTestClient 绑定真实安全链切片范式）、GatewaySecurityConfiguration.java（401/403/404 短路）、IdentityPropagationFilter.java（@Component 过滤器注册先例）

## 5. Dependencies

无外部 DU 依赖；任务内串行（common-core→common-web→13 client→gateway）。

## 6. Implementation Sketch

- TraceContext.isValid：`traceId != null && VALID.matcher(traceId).matches()`；VALID=`Pattern.compile("^[0-9a-f]{32}$")`。
- 拦截器：
  - 类声明 `implements ClientHttpRequestInterceptor`；`public static final TraceClientHttpRequestInterceptor INSTANCE = new ...()`；私有构造。
  - intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)：取 MDC；空白 generate；request.getHeaders().set(TraceConstants.TRACE_HEADER, traceId)；return execution.execute(request, body)。
- TraceIdFilter：把 `traceId == null || !VALID_TRACE_ID.matcher(traceId).matches()` 换成 `!TraceContext.isValid(traceId)`，删本地 Pattern 常量与 import。
- 13 client：在 `.defaultHeader(...)` 之后、`.build()` 之前插 `.requestInterceptor(TraceClientHttpRequestInterceptor.INSTANCE)`；import 5 行同类。每改完一个模块即编译。
- TraceIdWebFilter：
  - `@Component public class TraceIdWebFilter implements WebFilter, Ordered`；常量 `String TRACE_ATTR = TraceIdWebFilter.class.getName() + ".traceId"`。
  - filter：String incoming = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER)；String traceId = TraceContext.isValid(incoming) ? incoming : TraceContext.generate()；
    exchange.getResponse().getHeaders().set(TRACE_HEADER, traceId)；
    ServerHttpRequest mutated = exchange.getRequest().mutate().headers(h -> { h.remove(TRACE_HEADER); h.add(TRACE_HEADER, traceId); }).build()；
    exchange.getAttributes().put(TRACE_ATTR, traceId)；
    return chain.filter(exchange.mutate().request(mutated).build())；
  - getOrder 返回 Ordered.HIGHEST_PRECEDENCE。
- 网关测试装配：@SpringJUnitConfig 内嵌 @EnableWebFluxSecurity @Import({GatewaySecurityConfiguration.class, TraceWebTestConfig.class})；TraceWebTestConfig @Bean TraceIdWebFilter；WebHttpHandlerBuilder.webHandler(new WebHttpHandlerBuilder 装配方式参照 CHG0015 setUp（WebFilterChainProxy + bindToWebHandler），将 WebFilter 经 builder.webFilter(filter) 置于链首；下游桩从 exchange request 读 X-Trace-Id 写入响应头/或用 AtomicReference 捕获，500 用例桩设置 500 状态；admin token helper 复刻 CHG0015/0016。

## 7. Pseudocode

命中 complexity-trigger：reactive-filter（响应头时机与请求改写）。

```
// TraceIdWebFilter（reactive）
incoming = request.headers[X-Trace-Id]
traceId = isValid(incoming) ? incoming : generate()
response.headers.set(X-Trace-Id, traceId)      // 链前预置：短路/5xx 都携带
mutatedReq = request.mutate:
    headers.remove(X-Trace-Id)                 // 丢弃非法/多值
    headers.add(X-Trace-Id, traceId)
exchange.attributes[TRACE_ATTR] = traceId      // exchange 作用域，非 MDC
return chain.filter(exchange.mutate.request(mutatedReq))
```

## 8. 风险与回归边界

- 响应头必须在链执行前 set（reactive commit 后不可写）；预置不影响下游/安全链对其他 header 的操作。
- 拦截器对既有调用透明：defaultHeader 与 requestInterceptor 互不冲突；set 只影响 X-Trace-Id。
- 回归边界：common-core/common-web/gateway 全量；9 个业务/公共模块全量（13 client 编译与既有 MockServer 测试）。
