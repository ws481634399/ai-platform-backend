# DU Task Spec — DU-BE-002

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-002 引用该表，不新造 DU。

## 0. 元信息

- DU id: DU-BE-002
- Change ID: CHG-0025
- Feature Path: 分布式增强/Outbox 可靠投递/Outbox 投递能力/Outbox 可靠投递
- 权威来源: story-design.md §5 / DU-BE-002

## 任务清单

- [ ] 任务 1 — Flyway add_outbox_event 迁移（outbox_event 表 + idx_status_retry / idx_aggregate）（verifies: TC-001）
- [ ] 任务 2 — OutboxStatus 枚举 + OutboxEvent 实体 + OutboxEventRepository（含 claim/markSent/markFailedWithBackoff/resetForRetry/findPendingDue/findByFilters）（verifies: TC-001, TC-002, TC-003, TC-006）
- [ ] 任务 3 — OutboxRecordWriter.append（事务内写入，失败抛异常）（verifies: TC-001）
- [ ] 任务 4 — OutboxBackoffPolicy（有界指数退避 + maxRetries）（verifies: TC-003, TC-006）
- [ ] 任务 5 — EventRouter + SendTarget（订单事件默认路由，register 扩展点）（verifies: TC-002）
- [ ] 任务 6 — OutboxDeliveryTask（@Scheduled、CAS 抢占、同聚合顺序、路由发送、成功 SENT、失败退避/FAILED）（verifies: TC-002, TC-003, TC-004, TC-005, TC-006）
- [ ] 任务 7 — OutboxAdminController（GET 列表/详情、POST /retry、RBAC system:outbox:list/retry、审计）（verifies: TC-006, TC-007）
- [ ] 任务 8 — mall-identity 权限码 + 菜单 DML（verifies: TC-007）
- [ ] 任务 9 — 全量回归 mvn test（mall-order + mall-admin outbox，不破坏 M4/M5 既有）（verifies: TC-008）

## Acceptance Criteria

- [ ] AC-010 — @Transactional 内 append 成功则提交后 outbox_event 存在 PENDING；append 抛异常则业务事务回滚无记录
- [ ] AC-011 — 到期 PENDING 经 sendSync 成功后置 SENT+sent_at；发送消息 keys=envelope.eventId
- [ ] AC-012 — 发送失败保留 PENDING、retry_count+1、next_retry_at 退避；不删不标 FAILED
- [ ] AC-013 — next_retry_at 到期后下一轮自动续投直至 SENT，无需人工
- [ ] AC-014 — 同 aggregateId 多条按 created_at 顺序投递，前一条 SENT 后才投下一条
- [ ] AC-015 — 持续失败超 maxRetries → FAILED+last_error；mall-admin 查询可返回
- [ ] AC-017 — mall-order outbox 单测/集成测试全绿，M4/M5 既有订单/补偿用例零回退

## 执行顺序（Execution Order）

1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9

## 并行度（Parallelization）

无（核心状态机串行；任务 8 可与 7 并行但保持串行以简化）

## Verification

- Unit: `mvn -pl mall-order -am test`（OutboxBackoffPolicy 退避参数、EventRouter 路由、Repository 状态转换纯逻辑）
- Integration: SpringBootTest + H2 Flyway（OutboxDeliveryTask 多轮调度、同聚合顺序、事务回滚、失败退避/FAILED）
- API: MockMvc（OutboxAdminController list/get/retry + 权限码 + 审计）
- Migration: Flyway 前向执行 add_outbox_event；H2 内验证表/索引存在
- Error Case: sendSync 抛 MQClientException → 退避保留 PENDING；maxRetries 耗尽 → FAILED；未知 eventType → FAILED
