# DU Task Design — DU-BE-706

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-706 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-706 与同目录 task-spec.md（"做什么/验收"）配对互链。
> 本文件固定为 Expected Implementation（Plan / Sketch / Pseudocode），
> 与 implementation.md（Actual Implementation）分立，不得合并。

## 1. Goal

为 compensation_task 增加 trace_id 持久化能力：V3 加可空列，补偿登记写入触发请求的 traceId，管理端分页/详情出参携带，实现异步补偿与原始请求的可追溯关联。不改变补偿状态机/退避/调度/幂等等任何既有行为。

## 2. Repository

repo-1（implementation/ai-platform-backend），仅 mall-services/mall-order 一个 Maven 模块。

## 3. Scope

- 新增：`src/main/resources/db/migration/V3__compensation_trace_id.sql`；测试 CompensationTraceMigrationTest。
- 修改：CompensationTask（字段+双工厂+getter）、CompensationService（enqueue 读 MDC+日志）、CompensationTaskPo（属性）、MyBatisCompensationRepository（双向映射）、AdminOrderDtos.CompensationView（record 组件）、AdminCompensationController（toView）；CompensationService 登记单测与 OrderApiTest 锁成单败场景扩充。
- 不动：V1/V2 SQL、CompensationStatus、退避序列、@Scheduled、InventoryCompensationHandler、OrderCompensationPort 接口签名、权限码、分页结构。

## 4. Design References

- 外部 story-design.md §1（mall-order 改动）、§2（出参契约）、§3（V3 数据变更）、§4（错误处理）、§6（TC-005~007）
- 实证样板：
  - V2__compensation_init.sql（DDL 风格）、CompensationTask.java（register/reconstitute 双工厂与 final 字段风格）
  - MyBatisCompensationRepository.java（toPo/toDomain 手工映射位置）
  - CompensationService.java L54-72（enqueue 登记方法体）、AdminCompensationController.java L51-55（toView）
  - OrderApiTest.java L380-433（锁成单败→补偿台分页→retry 全链路）
  - common-core TraceConstants.MDC_KEY（traceId 键名）

## 5. Dependencies

无（仅用 common-core 既有 TraceConstants/slf4j MDC，order 已具备依赖；与 DU-BE-705 无编译依赖）。

## 6. Implementation Sketch

- V3 SQL 单行 ALTER（H2 MODE=MySQL 与 MySQL 8 均可）；列不设默认值，历史行 NULL。
- CompensationTask：private final String traceId（与 createdAt 同属不可变登记事实）；私有构造器参数列表末尾（updatedAt 前或后均可，reconstitute 同步）加 traceId；register(businessType, businessId, operation, payload, traceId, now)；reconstitute(..., String traceId, Instant createdAt, Instant updatedAt)；新增 traceId()。toPo 写出；update 路径 traceId 随字段同值回写（MP updateById 全字段，值不变）。
- CompensationService.enqueue：
  `String rawTraceId = MDC.get(TraceConstants.MDC_KEY); String traceId = (rawTraceId == null || rawTraceId.isBlank()) ? null : rawTraceId;`
  register 传 traceId；log.warn("登记库存补偿任务 orderNo={}, op={}, reason={}, inserted={}, traceId={}", ...)；traceId 为 null 时不额外报错（登记 catch 兜底语义不变）。
- Po：private String traceId + 标准 getter/setter；MP 下划线映射 trace_id（全局 map-underscore-to-camel-case 已在用）。
- CompensationView record 末尾追加 String traceId（@StringId id 等既有组件不动，JSON 新字段在末尾）；toView 加 task.traceId()。
- 测试：
  - CompensationTraceMigrationTest：@SpringBootTest @ActiveProfiles("test") @JdbcTest 风格按 order 既有迁移测试惯例（实施时打开 order 现有 Migration/RepositoryIntegration 测试对照注解）；JdbcTemplate queryForObject 查 H2 INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='COMPENSATION_TASK' AND COLUMN_NAME='TRACE_ID'：IS_NULLABLE='YES'、TYPE_NAME LIKE '%VARCHAR%'。
  - CompensationService 登记单测：mock CompensationRepository（insertIgnore Answer 捕获 task）、真实 ObjectMapper；MDC.put("traceId", 固定 32hex) 后调 enqueueInventoryRelease(非空 lines)，ArgumentCaptor 断言 task.traceId()=固定值，finally MDC.remove；再测 MDC 空时不抛异常且捕获值 null。
  - OrderApiTest：锁成单败场景的建单 MockMvc 请求加 .header("X-Trace-Id", 固定值)（经 TraceIdFilter 合法透传→MDC→登记）；分页 jsonPath $.data.records[0].traceId=固定值；retry $.data.traceId=固定值。

## 7. Pseudocode

命中 complexity-trigger：business-flow（登记 traceId 链路：入站 MDC → 应用服务 → 领域 → Po → 库 → 出参）。

```
// CompensationService.enqueue（HTTP 请求线程，TraceIdFilter 已写 MDC）
rawTraceId = MDC.get("traceId")
traceId = blank(rawTraceId) ? null : rawTraceId      // 防御非 HTTP 路径，不阻断
task = CompensationTask.register(TYPE_ORDER, orderNo, op, payload, traceId, now)
inserted = repository.insertIgnore(task)             // trace_id 同写入；唯一键冲突幂等语义不变

// admin 出参
toView(task): ..., task.traceId()                    // 历史行 → JSON null
```

## 8. 风险与回归边界

- register/reconstitute 签名变更点：全仓 grep 两方法调用处（repository、service、既有单测）逐一同步，漏点编译期即暴露。
- V3 只加可空列：不回填、不动唯一键/索引；对既有 SQL 行为零影响。
- 回归边界：mall-order 全量（含 OrderApiTest 补偿全链路、退避/调度单测、V1/V2 迁移）。
