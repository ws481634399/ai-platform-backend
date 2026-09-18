# DU Task Design — DU-BE-510

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。
> 本 DU 对应 STORY-005-01-01-02（搜索异常响应与降级）；ES Client 基线见 DU-BE-501。

## 1. Goal

B0501 SEARCH_UNAVAILABLE(503) / B0502 SEARCH_BAD_REQUEST(400) 错误码、SearchExceptionAdvice 三类 ES 异常归一、空结果 200 语义与 traceId 日志；不做 DB 降级。

## 2. Repository

repo-1（ai-platform-backend：mall-services/mall-search）

## 3. Scope

- domain.search：SearchErrorCode（B0501 503 / B0502 400）、SearchException。
- interfaces.rest（infrastructure.web）.SearchExceptionAdvice：@RestControllerAdvice 统一 UnifyResult 结构。
  - ElasticsearchException / ResponseException(404 index_not_found) / TransportException 及 cause 链中的 ConnectException、SocketTimeoutException → B0501。
  - IllegalArgumentException → B0502。
  - WARN 日志含 traceId（MDC 键沿用工程约定），body 不外泄主机/堆栈；未匹配异常继续抛出。
- 空结果路径：repository 返回空 SearchPage → 200 {items:[],total:0,...}，不进 Advice。
- src/test：SearchExceptionAdviceTest（MockMvc + cause 链矩阵）、EmptyResultIT（别名/无命中 200，无 ERROR 日志）。

## 4. Design References

- CHG-0020 requirement-design.md §2.4（错误口径）；STORY-005-01-01-02 story-design.md §1/§2/§4。

## 5. Dependencies

权威表：无。实际前置 DU-BE-501（ES Client/健康基线），同模块增量发布。

## 6. Implementation Sketch

```
查询链路（DU-BE-502/511）抛出 ES 异常
      └─ SearchExceptionAdvice @RestControllerAdvice
            ├─ 连接/超时/index_not_found → B0501 503（WARN+traceId，无主机/堆栈）
            └─ IllegalArgumentException → B0502 400
空结果：repository → 空 SearchPage → 200 items=[] total=0（不经 Advice）
```

## 7. Pseudocode

```
handle(ex, request):
  traceId = MDC.get("traceId"-键沿用工程约定)
  if anyMatch(throwableList(ex), ConnectException|SocketTimeoutException|
             ResponseException(404/index_not_found)|ElasticsearchException):
      log.warn("search unavailable trace={}", traceId, ex)
      return UnifyResult.fail(B0501, traceId)        // 503，无主机/堆栈
  if ex is IllegalArgumentException:
      return UnifyResult.fail(B0502, ex.message, traceId)  // 400
  throw ex
```
