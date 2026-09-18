# DU Task Spec — DU-BE-510

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本 DU 对应 STORY-005-01-01-02。

## 0. 元信息

- DU id: DU-BE-510
- Change ID: CHG-0020
- Feature Path: 商品搜索/商品搜索查询/搜索服务基础/搜索异常响应与降级
- 权威来源: STORY-005-01-01-02 story-design.md §5 / DU-BE-510

## 任务清单

- [ ] 任务 1 — SearchErrorCode（B0501/B0502）与 SearchException（verifies: TC-001@01-01-02, TC-002@01-01-02）
- [ ] 任务 2 — SearchExceptionAdvice：连接/超时/包装层 cause 链遍历归一 B0501（verifies: TC-001, TC-002, TC-006@01-01-02）
- [ ] 任务 3 — index_not_found（别名不存在）→ 503 B0501 而非原生响应（verifies: TC-003@01-01-02）
- [ ] 任务 4 — IllegalArgumentException → 400 B0502（为查询参数非法路径预留，随 502/511 联调验证）
- [ ] 任务 5 — 空结果 200 items=[] total=0 与无 ERROR 日志（verifies: TC-004@01-01-02）
- [ ] 任务 6 — MDC traceId 注入 WARN 日志与响应链路透传（verifies: TC-005@01-01-02）

## Acceptance Criteria

STORY-005-01-01-02（异常响应与降级）：

- [ ] AC-001 — ES 连接不可用 → 503 B0501 统一结构，无堆栈/主机泄漏。
- [ ] AC-002 — 查询超时 → B0501。
- [ ] AC-003 — 索引/别名不存在 → B0501（非原生 index_not_found）。
- [ ] AC-004 — 0 命中 → 200 items=[] total=0，无 ERROR。
- [ ] AC-005 — 不可用响应 WARN 日志含 traceId 可串联。

## 执行顺序（Execution Order）

1. 任务 1 → 2/3/4 → 5/6。

## 并行度（Parallelization）

任务 5 与 6 可并行；任务 2 依赖任务 1 与 DU-BE-501 基线。

## Verification

- Unit: Advice 映射单测（ElasticsearchException/ResponseException/TransportException cause 链矩阵）。
- Integration: Testcontainers ES 删除别名后查询 → 503 B0501；无命中 → 200 空页。
- API: MockMvc 断言 503/400/200 与 UnifyResult 结构，body 无堆栈/主机。
- Migration: N/A。
- Error Case: 连接拒绝/超时/索引缺失/traceId 透传四类。
