# DU Task — DU-BE-001

> Repository Delivery 的 DU 级任务细化（Expected Implementation）。
> 权威来源：workspace-source.tasks 中本 DU 小节；本文件是 repo 侧可执行副本，
> Agent 依据权威来源填写，允许按仓内实际情况微调并保持一致。
>
> Implementation Guidance 提示：Implementation Sketch 必填；
> Pseudocode 必填（complexity-trigger: orchestration）；Verification 必填。
> 本文件固定为 Expected Implementation（Plan / Sketch / Pseudocode / Verification），
> 与 implementation.md（Actual Implementation）分立，不得合并。

## 1. Goal

mall-common-core/web/log/test 四模块由壳转能力实现——统一响应结构、错误码分段、TraceId 上下文（core 纯 Java 零 Spring）；TraceIdFilter、GlobalExceptionHandler、BusinessException、springdoc 传递依赖（web WebMVC 增强层）；logback 基础日志配置（log）；测试依赖聚合器（test），并交付 core 单元测试 + common-web @WebMvcTest 切片测试 + common-test 占位测试。

## 2. Repository

repo-1（implementation/ai-platform-backend）

## 3. Scope

- mall-common-core：`UnifyResult<T>`（success/code/message/data/traceId + 静态工厂 ok/fail）、`ErrorCode` 接口 + A/B/S 分段常量、`TraceContext`（ThreadLocal，UUID 去横线 32 位）、`TraceConstants`（header=X-Trace-Id，MDC key=traceId）+ 对应单元测试；POM 零依赖保持
- mall-common-web：`BusinessException`（RuntimeException + ErrorCode + HTTP 语义）、`TraceIdFilter`（OncePerRequestFilter：生成/透传/MDC+TraceContext 写入/响应头回写）、`GlobalExceptionHandler`（参数异常→400+A 段、业务异常→异常携带码+HTTP 语义、未预期→500+S 段通用文案，响应体一律 UnifyResult，禁止泄露 StackTrace/SQL/凭据）+ @WebMvcTest 切片测试；POM 增加 mall-common-core（web→core 方向）与 springdoc-openapi-starter-webmvc-ui（版本走 BOM）
- mall-common-log：`logback/mall-logback-base.xml`（console appender，pattern 含 %X{traceId}），零新增依赖
- mall-common-test：聚合 spring-boot-starter-test + h2（均由 Boot BOM 托管）+ 1 个占位单元测试
- 禁止事项：core 不出现任何 Spring 依赖（AC-12）；common 四模块不出现业务领域模型（AC-06）；不产生对外业务 Controller

## 4. Design References

- design.md §2.2 BOM 策略（Flyway/H2/JUnit 由 Boot BOM 托管，BOM 零变更）
- design.md §2.3 Web Foundation（接口契约：统一响应 JSON `{success, code, message, data, traceId}`；TraceId header=`X-Trace-Id`、MDC key=`traceId`；错误码分段 0/A/B/S）
- design.md §2.4 common/contracts 边界不变量
- design.md §2.8 Test Foundation（聚合器与 core/web 测试部分）
- design.md §2.9 关键组件清单

## 5. Dependencies

无（本 DU 为 DU-BE-002 的前置：产物需 `mvn install` 后方可被服务引用）

## 6. Acceptance Criteria

- AC-06 复验通过（common 无业务污染）
- AC-10 通过（统一响应可使用、参数/系统异常转换、TraceId 进响应与日志——以 @WebMvcTest 切片测试为验证形态）
- AC-12 core 部分通过（mall-common-core 依赖树仍仅自身坐标）
- mall-common 四模块 `mvn test` 全绿

## 7. Implementation Sketch

```text
请求处理协作关系（mall-common-web 内，供服务继承）:
TraceIdFilter (OncePerRequestFilter)
    ├── 读取请求头 X-Trace-Id（合法性校验：32 位十六进制）
    ├── 无/不合法 → TraceContext.generate() 生成
    ├── 写入 MDC（key=traceId）+ TraceContext（ThreadLocal）
    ├── doFilterChain → 业务处理（本 Change 无业务 Controller）
    ├── 响应头回写 X-Trace-Id
    └── finally: TraceContext.clear()（防线程池污染）
异常 → GlobalExceptionHandler (@RestControllerAdvice)
    ├── MethodArgumentNotValidException/BindException → UnifyResult.fail(A 段码, 参数文案), HTTP 400
    ├── BusinessException → UnifyResult.fail(异常携带 ErrorCode), HTTP 语义由异常携带
    └── 其他未预期 → UnifyResult.fail(S 段通用文案), HTTP 500（不泄露 StackTrace/SQL）
响应出口统一: UnifyResult{success, code, message, data, traceId}
装配方式: WebFoundationAutoConfiguration（AutoConfiguration.imports 注册，服务零声明即获；
         @ConditionalOnMissingBean 允许服务覆盖）注册 TraceIdFilter(FilterRegistrationBean,
         最高优先级) 与 GlobalExceptionHandler
测试资产: core 纯单测（TraceContext/UnifyResult/错误码分段）
         web @WebMvcTest 切片（Filter 行为 + 三类异常路径 + 响应 JSON 断言）
```

## 8. Pseudocode

（complexity-trigger: orchestration → 必填；Filter/Handler/TraceContext 多组件协作 + 关键异常分支）

```text
doFilterInternal(request, response, chain):
    traceId = request.getHeader("X-Trace-Id")
    if traceId == null or not matches ^[0-9a-f]{32}$:
        traceId = TraceContext.generate()          # UUID 去横线 32 位
    TraceContext.set(traceId); MDC.put("traceId", traceId)
    try:
        chain.doFilter(request, response)
    finally:
        response.setHeader("X-Trace-Id", traceId)   # 响应头回写
        MDC.remove("traceId"); TraceContext.clear() # 防线程池污染

handleException(ex):                              # @RestControllerAdvice 分派
    if ex is MethodArgumentNotValidException or BindException:
        return UnifyResult.fail(ErrorCode.A_PARAM, 提取字段错误文案)   # HTTP 400
    if ex is BusinessException:
        return UnifyResult.fail(ex.errorCode, ex.message)              # HTTP 语义随异常
    log.error("unhandled", ex)                     # 服务端留痕
    return UnifyResult.fail(ErrorCode.S_INTERNAL, "系统繁忙，请稍后重试")  # HTTP 500，不泄露内部信息
```

## 9. Verification

- Unit（core）: TraceContext 生成/设置/获取/清理与线程隔离；UnifyResult ok/fail 工厂字段完整性；错误码分段常量归属正确
- Integration（web @WebMvcTest 切片）: 参数异常→400+A 段 JSON；BusinessException→业务码 JSON；未预期异常→500+通用文案且 JSON 不含 StackTrace/SQL/类名；MockMvc 断言响应头 X-Trace-Id 与 UnifyResult.traceId 一致、请求携带合法 TraceId 时透传不重生成
- Error Case: Filter 异常路径验证 finally 清理生效（二次请求无残留）；core 依赖树复验 `mvn dependency:tree -pl mall-common/mall-common-core` 仅自身坐标
- 构建门: mall-common 聚合 `mvn clean test` 全绿
