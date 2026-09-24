# DU Task Spec — DU-BE-705

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-705 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-705 与同目录 task-design.md（"怎么做"）配对互链。
> verifies 约定：任务项绑定 TC-NNN（红绿灯对象）；TC 定义在外部对应 Story 的 test-design.md，本文件只引用不新造。

## 0. 元信息

- DU id: DU-BE-705
- Change ID: CHG-0023
- Feature Path: 平台验收补强/M5 验收缺口补强/可观测与配置/Trace 贯通（跨服务链路与补偿追踪）
- 权威来源: story-design.md §5 / DU-BE-705

## 任务清单

- [ ] 任务 1 — mall-common-core TraceContext 增加 isValid(String) 公共合法性判定（^[0-9a-f]{32}$，null/空白 false），Pattern 提为私有常量（verifies: TC-003）
- [ ] 任务 2 — mall-common-web 新增 trace/TraceClientHttpRequestInterceptor（无状态单例 INSTANCE；读 MDC，无则 generate；set 单值 X-Trace-Id；不回写 MDC）；TraceIdFilter 改调 TraceContext.isValid 并删除本地正则，行为不变（verifies: TC-001, TC-003）
- [ ] 任务 3 — 新增 TraceClientHttpRequestInterceptorTest 三分支（透传/无上下文生成不污染 MDC/脏头单值覆盖）（verifies: TC-001）
- [ ] 任务 4 — 13 个 internal RestClient builder 链逐一追加 .requestInterceptor(TraceClientHttpRequestInterceptor.INSTANCE)（story-spec §6 清单：order×4、cart×2、product×2、inventory×1、member×1、identity×1、search×1、common-config×1），其余逻辑零改动（verifies: TC-002, TC-007）
- [ ] 任务 5 — mall-gateway pom 增加 mall-common-core；新增 trace/TraceIdWebFilter（@Component WebFilter+Ordered HIGHEST_PRECEDENCE；合法透传/非法重生；链前预置响应头；mutate 请求去旧头加最终值；exchange 属性存值）（verifies: TC-004）
- [ ] 任务 6 — 新增 TraceIdWebFilterTest（WebTestClient 切片：无/合法/非法头 + 401/403/404/5xx 响应头 + 下游收值断言）（verifies: TC-004）
- [ ] 任务 7 — 静态接线核对证据归档 evidence/logs/trace-interceptor-wiring.txt；全模块回归（common-core/common-web/order/cart/product/inventory/member/identity/search/common-config/gateway）全绿（verifies: TC-002, TC-007）

## Acceptance Criteria

- [ ] AC-010 — 拦截器单测证明有上下文出站同值、无上下文生成合法新值且不污染 MDC、脏头被单值覆盖（TC-001）；13 个 internal client 100% 接线并有静态勾稽证据（TC-002）
- [ ] AC-011 — 网关切片测试证明无/非法入站生成、合法透传，401/403/404/5xx 所有响应均回写 X-Trace-Id 且下游收到最终值（TC-004）
- [ ] AC-014 — TraceIdFilter 既有 5 例零修改全绿（TC-003）；13 client 所在全部服务与网关全量测试零失败零回退（TC-007）

## 执行顺序（Execution Order）

1. 任务 1（公共能力先行）
2. 任务 2 → 任务 3（拦截器实现+测试红绿）
3. 任务 4（13 client 接线，每模块编译跟随）
4. 任务 5 → 任务 6（网关实现+测试）
5. 任务 7（接线证据 + 全模块回归收口）

## 并行度（Parallelization）

无（仓内串行；与 DU-BE-706 无代码依赖，可并行，本 DU 先行为 706 提供 isValid 公共能力但 706 不使用）

## Verification

- Unit: `mvn -pl mall-common/mall-common-core test`、`mvn -pl mall-common/mall-common-web test`；TraceClientHttpRequestInterceptorTest（mock ClientHttpRequestExecution）；既有 TraceIdFilterTest 5 例
- Integration: mall-gateway TraceIdWebFilterTest（@SpringJUnitConfig + 真实 GatewaySecurityConfiguration + WebTestClient，下游桩捕获入站头、可控 500）
- API: TC-004 覆盖 200/401/403/404/500 响应头矩阵与下游转发值一致性
- Static: TC-002 13 builder 点 requestInterceptor 勾稽（输出归档 evidence/logs）
- Regression: 13 client 所在 9 模块 + common-core/common-web + gateway 全量 `mvn test`
- Migration: N/A（本 DU 无数据库变更）
- Error Case: 非法/缺失/多值入站头、MDC 缺失、下游 5xx 均有断言
