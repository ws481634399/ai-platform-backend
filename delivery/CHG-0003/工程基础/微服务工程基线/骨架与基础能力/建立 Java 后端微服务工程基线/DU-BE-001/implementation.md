# DU Implementation — DU-BE-001

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库，Workspace 仅保留引用。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task.md（Expected Implementation）分立，不得合并。

## 变更内容

四模块由壳转能力实现，全部位于 `mall-common/`：

### mall-common-core（纯 Java，零 Spring 依赖）
- `result/ErrorCode.java`：错误码接口（code/message 两方法）
- `result/CommonErrorCode.java`：公共错误码枚举（0 成功 / A0001 参数 / B0001 业务 / S0001 系统，分段与 AC-10 一致）
- `result/UnifyResult.java`：统一响应结构（success/code/message/data/traceId，ok 与 fail 静态工厂，traceId 从 TraceContext 读取）
- `trace/TraceContext.java`：ThreadLocal TraceId 上下文（set/get/clear/generate，generate 产出 32 位 hex）
- `trace/TraceConstants.java`：请求头（X-Trace-Id）与 MDC key 常量
- 测试：`CommonErrorCodeTest`（6）/`UnifyResultTest`（5）/`TraceContextTest`（6），共 17 tests 全绿

### mall-common-web（WebMVC 增强层，spring-boot-starter-web + springdoc 传递）
- `trace/TraceIdFilter.java`：OncePerRequestFilter——请求头合法（32 位 hex）透传，否则重生成；写入 MDC 与 TraceContext；finally 中回写响应头并清理上下文
- `exception/BusinessException.java`：携带 ErrorCode 与可选 HttpStatus 的业务异常
- `advice/GlobalExceptionHandler.java`：@RestControllerAdvice——BindException→400/A0001（含字段明细）、BusinessException→业务码+语义 HTTP 状态、Exception→500/S0001（日志记录但不回传内部信息）
- `config/WebFoundationAutoConfiguration.java`：注册 TraceIdFilter 与 GlobalExceptionHandler，`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动装配入口
- 测试：`GlobalExceptionHandlerTest`（6）/`TraceIdFilterTest`（5）@WebMvcTest 切片 + `testsupport/`（TestApplication/TestEchoController/ValidRequest），共 11 tests 全绿

### mall-common-log（日志基础资产）
- `logback/mall-logback-base.xml`：基础日志配置资产（console pattern 含 %X{traceId:-}），随 jar 提供
- 主代码仅 package-info（资产型模块）

### mall-common-test（测试依赖聚合器）
- POM 聚合 spring-boot-starter-test + H2（test scope 传递），无主代码
- 测试：`CommonTestAggregatorTest` + package-info 占位，验证聚合器可用

## Commits

无（本地实施完成，尚未提交；提交待用户确认后执行）

## Deviations

### DEV-1
- 原 DU 建议: @WebMvcTest 切片测试直接发现 test classpath 中的测试配置类
- 实际实现: GlobalExceptionHandlerTest / TraceIdFilterTest 显式声明 `@ContextConfiguration(classes = {TestApplication.class, TestEchoController.class})` 并保留 @Import(WebFoundationAutoConfiguration)
- 原因: TestApplication 位于 `testsupport` 子包，@WebMvcTest 自测试类包向上搜索 @SpringBootConfiguration 不可达；TestApplication 无组件扫描，TestEchoController 需显式注册进上下文
- 影响评估: 测试语义与覆盖不变（11 tests 全绿）；显式声明规避隐式搜索，更稳健，无生产代码影响

## 自检

- `mvn test -pl mall-common/mall-common-web -am`（含 core）BUILD SUCCESS：core 17 tests + web 11 tests 全绿（2026-09-01 22:14，exit 0）
- mall-common-test 聚合器 jar 构建成功（空内容 jar 符合依赖聚合器定位）
- 全量 `mvn test` Reactor 24/24 SUCCESS（57.8s），四模块测试全部通过——详见 DU-BE-002 留证
- 沙箱环境构建统一追加 `-Dmaven.repo.local=d:\Desktop\ai-platform\.m2-sandbox`（工作区内仓库，规避工作区外写拦截），属环境适配，非实现偏离
