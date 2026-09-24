# DU Task Spec — DU-BE-706

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> verifies 约定：任务项绑定 TC-NNN（红绿灯对象）；TC 定义在外部对应 Story 的 test-design.md，本文件只引用不新造。

## 0. 元信息

- DU id: DU-BE-706
- Change ID: CHG-0023
- Feature Path: 平台验收补强/M5 验收缺口补强/可观测与配置/Trace 贯通（跨服务链路与补偿追踪）
- 权威来源: story-design.md §5 / DU-BE-706

## 任务清单

- [ ] 任务 1 — mall-order 新增 V3__compensation_trace_id.sql（ALTER TABLE compensation_task ADD COLUMN trace_id VARCHAR(32) NULL，updated_at 之后，无索引）（verifies: TC-005）
- [ ] 任务 2 — CompensationTask 聚合增加 final traceId 字段：register 末位加参、reconstitute 加参、traceId() getter；成功/失败/rearm 状态流转不改动该字段（verifies: TC-005）
- [ ] 任务 3 — CompensationService.enqueue 方法体内读 MDC.get(TraceConstants.MDC_KEY)（空白归 null），传入 register；登记 warn 日志追加 traceId；MDC 缺失不抛错；3 个 Port 调用点签名不变（verifies: TC-005）
- [ ] 任务 4 — CompensationTaskPo 加 traceId 属性（驼峰自动映射）；MyBatisCompensationRepository toPo/toDomain 双向映射（verifies: TC-005）
- [ ] 任务 5 — AdminOrderDtos.CompensationView 末尾加 String traceId；AdminCompensationController.toView 传 task.traceId()（分页与 retry 详情同时生效）（verifies: TC-006）
- [ ] 任务 6 — 新增/扩充测试：CompensationTraceMigrationTest（列存在+可空）、CompensationService 登记单测（MDC 固定值/缺失两分支）、OrderApiTest 锁成单败场景固定 X-Trace-Id 断言分页与 retry 详情 traceId（verifies: TC-005, TC-006）
- [ ] 任务 7 — mall-order clean test 全量回归零失败（verifies: TC-007）

## Acceptance Criteria

- [ ] AC-012 — V3 在 H2 测试库执行成功，compensation_task.trace_id 为 VARCHAR(32) 可空（TC-005）；新登记补偿写入登记时刻 MDC traceId（HTTP 请求线程三调用点路径），MDC 缺失容错 null 且不阻断登记（TC-005 单测）
- [ ] AC-013 — admin 补偿分页 records[].traceId 与 retry 详情 data.traceId 均正确返回（TC-006）；历史行 NULL 经 reconstitute/toView 序列化为 null 不报错（TC-005 迁移+映射）
- [ ] AC-014 — mall-order 全量测试零失败零回退（TC-007）；补偿退避/幂等/手动重试既有行为不变

## 执行顺序（Execution Order）

1. 任务 1（迁移先行，H2 验证）
2. 任务 2 → 任务 3 → 任务 4（领域→应用→持久化自内向外）
3. 任务 5（出参）
4. 任务 6（测试红绿）
5. 任务 7（order 全量回归）

## 并行度（Parallelization）

无（仓内串行；与 DU-BE-705 无代码依赖，可并行；排期在 705 之后）

## Verification

- Unit: CompensationService 登记单测（Mockito repository + 真实 ObjectMapper；MDC put/remove finally 清理）；CompensationTask 工厂/流转既有单测回归
- Integration: CompensationTraceMigrationTest（@SpringBootTest test profile H2 Flyway，JdbcTemplate INFORMATION_SCHEMA.COLUMNS 断言列名/类型/可空）；OrderApiTest 锁成单败场景（MockMvc 请求带固定 X-Trace-Id，分页+retry 视图断言）
- API: TC-006 覆盖 GET /api/admin/compensations 与 POST /{id}/retry 两出口 traceId 字段
- Migration: V3 ADD COLUMN NULL 无默认值，H2 MODE=MySQL 绿；MySQL 8 标准 DDL 留联调
- Regression: `mvn -pl mall-services/mall-order clean test -Dsurefire.failIfNoSpecifiedTests=false`
- Error Case: MDC 缺失（非 HTTP 防御路径）traceId null 且登记不失败
