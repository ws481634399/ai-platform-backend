# DU Task Spec — DU-BE-003

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-003 引用该表，不新造 DU。
> verifies 约定：任务项绑定 TC-NNN（红绿灯对象）；TC 定义在外部对应 Story 的 test-design.md，本文件只引用不新造。

## 0. 元信息

- DU id: DU-BE-003
- Change ID: CHG-0025
- Feature Path: 分布式增强/订单集成事件与库存异步消费者/订单事件与库存消费能力/订单集成事件与库存异步消费者
- 权威来源: story-design.md §5 / DU-BE-003

## 任务清单

- [ ] 任务 1 — 领域事件类型（OrderIntegrationEventType/OrderIntegrationEvent）+ Order 聚合四迁移点收集与 pullIntegrationEvents（reconstitute 空列表）（verifies: TC-004）
- [ ] 任务 2 — OrderEnvelopeAssembler：四事件 Payload DTO 组装 + Envelope（含 paymentNo/reservationNo/paymentDeadline 裁决字段）（verifies: TC-001）
- [ ] 任务 3 — OrderEventOutbox 端口 + OutboxOrderEventFlusher（async flush/sync 丢弃）+ IntegrationMode（verifies: TC-002）
- [ ] 任务 4 — MyBatisOrderRepository insert/transition 事务内 flush 挂点（构造器依赖）（verifies: TC-002）
- [ ] 任务 5 — PaymentService/OrderCancelService 分支：async 不调同步路径、sync 降级 confirm/release + WARN + 失败补偿（verifies: TC-003）
- [ ] 任务 6 — OrderStatusInternalController（GET status，404）+ CompensationInternalController（POST，type 校验，调补偿端口）（verifies: TC-005）
- [ ] 任务 7 — mall-inventory：pom 依赖 + 仓储 findReservationIdsByOrderNo（@Select LIKE）+ OrderServiceClient（getStatus/registerCompensation）（verifies: TC-006, TC-007）
- [ ] 任务 8 — PaymentSucceededInventoryHandler + OrderCancelledInventoryHandler（乱序裁决/逐行 confirm·release/失败登记补偿后抛出，@ConditionalOnProperty）（verifies: TC-006, TC-007）
- [ ] 任务 9 — 两侧 application.yml：rocketmq 段（env 注入）+ inventory order-uri/internal secret（verifies: TC-003）
- [ ] 任务 10 — 全量回归 mvn test（mall-order + mall-inventory，既有 M2/M4/M5 零回退）（verifies: TC-008）

## Acceptance Criteria

- [ ] AC-018 — 四迁移事务提交后 outbox_event 各有对应 PENDING 记录且 payload 为 §41 字段 Envelope；flush 失败事务回滚无记录
- [ ] AC-019 — PAYMENT_SUCCEEDED 经消费处理后预留转 DEDUCTED（Story 级由 handler 单测验证逐行 confirm；真实运行态 Integration Gate 1）
- [ ] AC-020 — ORDER_CANCELLED 经消费处理后预留 RELEASED、Available 恢复（Story 级 handler 单测；Integration Gate 2）
- [ ] AC-021 — 重复投递：eventId 占位（S1 链）+ 业务 CAS 双层，库存只变化一次
- [ ] AC-022 — 已取消收 PAYMENT_SUCCEEDED、COMPLETED/PAID/SHIPPED 收 ORDER_CANCELLED → markSkipped+WARN，库存不变
- [ ] AC-023 — 业务失败时经 internal 端点登记 INVENTORY_CONFIRM_DEDUCT/INVENTORY_RELEASE 补偿，可查询（登记幂等）
- [ ] AC-024 — rocketmq.enabled=false 时支付/取消走同步内部 API + 降级日志，库存终态与异步一致
- [ ] AC-025 — 两模块全量测试通过，既有功能零回退

## 执行顺序（Execution Order）

1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10

## 并行度（Parallelization）

无（发布侧任务 1~6 串行；消费侧 7~8 依赖发布契约；配置与回归收口）

## Verification

- Unit: `mvn -pl mall-services/mall-order,mall-services/mall-inventory -am test`——Assembler/Flusher/Service/聚合/Handler 全部 Mockito 单测
- Integration: N/A（跨服务真实投递→消费→库存状态变化归 M7 Integration Gate，Testcontainers RocketMQ）
- API: internal 控制器直调测试（DTO/404/参数校验/补偿端口调用）
- Migration: N/A（本 DU 无新迁移；consumed_event 建表归 STORY-009-05-01）
- Error Case: 状态查询失败抛出重试；confirm/release 异常登记补偿后抛出；sync 模式事件丢弃零 outbox 行
