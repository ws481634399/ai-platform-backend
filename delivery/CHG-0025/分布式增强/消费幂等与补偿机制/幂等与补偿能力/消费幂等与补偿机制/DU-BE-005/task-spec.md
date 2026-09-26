# DU Task Spec — DU-BE-005

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §6（SSOT）；本文件只按 DU id DU-BE-005 引用该表，不新造 DU。
> verifies 约定：任务项绑定 TC-NNN（红绿灯对象）；TC 定义在外部对应 Story 的 test-design.md，本文件只引用不新造。

## 0. 元信息

- DU id: DU-BE-005
- Change ID: CHG-0025
- Feature Path: 分布式增强/消费幂等与补偿机制/幂等与补偿能力/消费幂等与补偿机制
- 权威来源: story-design.md §1 / DU-BE-005

## 任务清单

- [ ] 任务 1 — mall-inventory V3__create_consumed_event.sql（DDL 对齐 ConsumedEventRepository.ddl）（verifies: TC-001）
- [ ] 任务 2 — mall-order V6__create_consumed_event.sql（同构，DEV-1）（verifies: TC-001）
- [ ] 任务 3 — CompensationTask：OP_AUTO_CANCEL_ORDER 常量 + manualComplete(now) 聚合方法（保留 lastError+标记）（verifies: TC-006）
- [ ] 任务 4 — OrderAutoCancelCompensationPayload + OrderAutoCancelCompensationHandler（systemCancel COMPENSATION）（verifies: TC-004）
- [ ] 任务 5 — CompensationActionHandler 接口；InventoryCompensationHandler 改为 implements（行为不变）（verifies: TC-005）
- [ ] 任务 6 — CompensationService：enqueueOrderAutoCancel（insertIgnore）+ 执行器列表按 supports 分派 + execute 包 MDC traceId/finally remove（verifies: TC-005, TC-009）
- [ ] 任务 7 — PaymentTimeoutCheckHandler：systemCancel 非 CONFLICT 异常时登记 ORDER_AUTO_CANCEL 后重抛（verifies: TC-003）
- [ ] 任务 8 — CompensationRepository.page 扩 businessType/businessId 参数；MyBatis 实现两过滤（空忽略）（verifies: TC-007）
- [ ] 任务 9 — AdminCompensationController：operation 白名单/aggregateId LIKE 转义筛选；POST /{id}/complete + compensation-audit 审计；CompensationView 增 payload（verifies: TC-006, TC-007）
- [ ] 任务 10 — mall-identity V15：system:compensation:list/retry/complete + 菜单 permission_code 更新 + SUPER_ADMIN 授权（verifies: TC-008）
- [ ] 任务 11 — 全量回归 mall-order + mall-inventory（既有 M4/M5/S1~S4 零回退）（verifies: TC-011）

## Acceptance Criteria

- [ ] AC-033 — 事件成功处理后 consumed_event 留痕（event_id 唯一键等七字段）
- [ ] AC-034 — 重复 eventId 占位命中 DUPLICATE 直接 ACK，业务不重复执行
- [ ] AC-035 — 消费失败登记对应 CompensationTask（含 ORDER_AUTO_CANCEL），同业务操作仅一条
- [ ] AC-036 — 30s 扫描执行，成功标完成；失败有界退避
- [ ] AC-037 — 超限 FAILED_DEAD；可按 operation/状态/聚合筛选并查看 payload
- [ ] AC-039 — 补偿执行沿用原 traceId（MDC），eventId 全链路一致
- [ ] AC-040 — 单测+集成测试通过，全量回归零回退

## 执行顺序（Execution Order）

1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11（任务 1/2 可并行；迁移先行）

## 并行度（Parallelization）

任务 1/2 可并行（不同模块）；其余按序，任务 4/5 可同批提交。

## Verification

- Unit: `mvn -pl mall-services/mall-order,mall-services/mall-inventory test`（Mockito 单测）
- Integration: H2 MySQL 模式 + Flyway 全迁移（V3/V6）、占位唯一键冲突
- Error Case: 登记失败不掩盖原异常；manualComplete 404/SUCCESS 语义
