# DU Implementation — DU-BE-002

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。

## 变更内容

- **任务 1**（Flyway V4）：mall-order `V4__add_outbox_event.sql`——outbox_event 表（id/aggregate_id/event_type/payload JSON/status/retry_count/next_retry_at/trace_id/last_error/created_at/sent_at）+ idx_status_retry(status,next_retry_at) + idx_aggregate(aggregate_id,created_at)。
- **任务 2**（domain + persistence）：`OutboxStatus` 枚举（PENDING/SENDING/SENT/FAILED）、`OutboxEvent` 实体、`OutboxEventRepository` 端口（save/findPendingDue/claim/markSent/markFailed/requeueWithBackoff/resetForRetry/findById/page）；MyBatis-Plus 实现 `OutboxEventPo`/`OutboxEventMapper`（@Update CAS SQL）/`MyBatisOutboxEventRepository`（H2 MySQL 模式集成测试验证 CAS 原子性与状态流转）。
- **任务 3**（OutboxRecordWriter）：`@Transactional(MANDATORY)` append——序列化 Envelope 为 payload JSON、status=PENDING、trace_id=envelope.traceId；失败抛异常触发业务回滚（AC-010）。
- **任务 4**（OutboxBackoffPolicy）：有界指数退避 nextRetryAt = min(initialDelay * factor^retryCount, maxDelay)；maxRetries 可配（默认 10）；shouldFail 判断超限。
- **任务 5**（EventRouter + SendTarget）：eventType → SendTarget{topic, delayLevel} 路由表；@PostConstruct 注册订单四事件→aimall-order-events（delayLevel=0）；register() 扩展点供 STORY-009-04-01 延迟路由；未知 eventType 抛 IllegalArgumentException（直接 FAILED 不重试）。
- **任务 6**（OutboxDeliveryTask）：@Scheduled(fixedDelay=5s) 扫描 findPendingDue(batch=100) → 按 aggregateId 分组取首条（同聚合顺序）→ CAS claim（PENDING→SENDING）→ route → sendSync/sendDelay → 成功 markSent；失败未超限 requeueWithBackoff（回 PENDING+退避）、超限 markFailed；producer 未装配（rocketmq.enabled=false）按失败退避保留 PENDING；单条异常不中断整轮。
- **任务 7**（mall-admin OutboxAdminController）：GET /api/admin/outbox/events（status/eventType/aggregateId 分页筛选）、GET /{id}、POST /{id}/retry（FAILED→PENDING 重置 retry_count，写 outbox-audit 日志含 operator/time/before-after status）；权限码 system:outbox:list / system:outbox:retry。
- **任务 8**（mall-identity V13）：system:outbox:list/retry 权限码 + 「分布式增强」目录 + 「Outbox 事件」页面（component_key=OutboxList）+ 超管授权。
- **任务 9**（回归）：mall-order `mvn test` **46/46 全绿**（Outbox 24 + 既有 22），M4/M5 订单/补偿用例零回退。
- **附带修复**：mall-common-mq `AutoConfiguration.imports` 误用 spring.factories 键值格式 → 改为纯类名列表（Spring Boot 3 自动装配文件格式），修复任何依赖 mall-common-mq 的应用上下文加载失败。

## Commits

| Commit | 任务 | 说明 |
| --- | --- | --- |
| e375c40 | 任务 1~9 + 附带修复 | CHG-0025 STORY-009-02-01 Outbox 可靠投递（DU-BE-002）——表/Writer/投递任务/admin API/权限 + mall-common-mq 自动装配修复 + 24 测试 |

- 本地提交，未推送。

## Deviations

### DEV-1
- 原 DU 建议: story-design 中投递任务对未知 eventType 记 WARN 标 FAILED。
- 实际实现: EventRouter.route 对未知 eventType 抛 IllegalArgumentException，delivery 任务 catch 后 markFailed（不重试）。
- 原因: 路由失败属配置错误，退避重试无意义；统一在 catch IllegalArgumentException 分支处理。
- 影响评估: 行为一致（FAILED + lastError），无功能影响。

### DEV-2
- 原 DU 建议: 审计用独立审计表/切面。
- 实际实现: 重投操作写 `outbox-audit` logger（结构化日志含 operator/time/beforeStatus/afterStatus）。
- 原因: 代码库尚无通用审计框架（Compensation 手动重试亦未接审计），首期以结构化日志满足"审计记录"要求；后续若引入审计框架可平滑替换。
- 影响评估: 可追溯性满足 AC-016；无数据模型变更。
