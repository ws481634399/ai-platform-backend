# DU Task Spec — DU-BE-004

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §0（SSOT）；本文件只按 DU id DU-BE-004 引用该表，不新造 DU。
> verifies 约定：任务项绑定 TC-NNN（红绿灯对象）；TC 定义在外部对应 Story 的 test-design.md，本文件只引用不新造。

## 0. 元信息

- DU id: DU-BE-004
- Change ID: CHG-0025
- Feature Path: 分布式增强/延迟订单自动取消/延迟取消能力/延迟订单自动取消
- 权威来源: story-design.md §1 / DU-BE-004

## 任务清单

- [ ] 任务 1 — V5 迁移 outbox_event 加 delay_level 列；OutboxEvent/OutboxEventPo/OutboxEventMapper/repository save 承载；OutboxRecordWriter 四参 append（三参委托）；OutboxAdminDtos.OutboxView 追加字段（verifies: TC-003）
- [ ] 任务 2 — pom 引入 mall-common-config；PaymentTimeoutPolicy（SystemParameterProvider + yml 默认值，动态读取）（verifies: TC-002）
- [ ] 任务 3 — DelayLevelMapper（18 级向上对齐/封顶/非法值/forceLevel 覆盖）（verifies: TC-001）
- [ ] 任务 4 — OrderIntegrationEventType 追加 PAYMENT_TIMEOUT_CHECK；Order.create 追加事件（与 ORDER_CREATED 同列表）（verifies: TC-002）
- [ ] 任务 5 — OrderEnvelopeAssembler 扩展（assemble 带 timeoutMinutes；延迟 payload=OrderDelayPayload；paymentDeadline 改传入值；toTag 映射）；OutboxOrderEventFlusher（一次 flush 读一次超时；延迟 append 带级别；普通 delayLevel=0）（verifies: TC-002）
- [ ] 任务 6 — EventRouter 注册 PAYMENT_TIMEOUT_CHECK→aimall-order-delay；OutboxDeliveryTask 发送分支读取 event.getDelayLevel()（verifies: TC-003）
- [ ] 任务 7 — OrderCancelService 重构：抽 doCancel 共用 + systemCancel(orderId,reason,source)，operator="SYS:source"；原会员 cancel 薄化（verifies: TC-005）
- [ ] 任务 8 — PaymentTimeoutCheckHandler（五状态分支/订单不存在 skip/409 归并 skip/正常 systemCancel，@ConditionalOnProperty + 监听注解）（verifies: TC-004）
- [ ] 任务 9 — OrderRepository 端口 findExpiredPending + Mapper @Select + Impl；OrderTimeoutFallbackScanner（@Scheduled/开关/逐单 systemCancel/异常隔离）（verifies: TC-006）
- [ ] 任务 10 — DelayTaskAdminMapper（union 三源+count）+ DelayTaskAdminController（GET 分页/POST 手动取消 + order-delay-audit 审计）（verifies: TC-007）
- [ ] 任务 11 — mall-identity V14__order_delay_permissions.sql（两权限/菜单页 DelayTaskList/SUPER_ADMIN 授权）（verifies: TC-008）
- [ ] 任务 12 — mall-order application.yml：mall.order.delay.force-level + timeout-fallback 开关/周期（env 注入）（verifies: TC-002, TC-006）
- [ ] 任务 13 — 全量回归 mvn -pl mall-services/mall-order -am test（既有 M4/M5/S1~S3 零回退）（verifies: TC-010）

## Acceptance Criteria

- [ ] AC-026 — 建单事务提交后 outbox_event 同事务出现 ORDER_CREATED(delay_level=0) 与 PAYMENT_TIMEOUT_CHECK(delay_level 映射正确)；payload 含 orderId/orderNo/expireAt
- [ ] AC-027 — 延迟到期消费者回查：PENDING_PAYMENT→自动取消（CANCELLED+PAYMENT_TIMEOUT），必经 OrderCancelService；Story 级 handler/service 单测，真实短延迟运行态 Integration Gate
- [ ] AC-028 — PAID/CANCELLED/COMPLETED/SHIPPED 订单延迟消息到期 ACK 跳过，订单不被取消
- [ ] AC-029 — 重复投递：eventId 占位 + CAS 双层幂等，只触发一次取消
- [ ] AC-030 — 延迟路径禁用/消息丢失时 fallback 扫描兜底；双路径并发 CAS 无双重取消
- [ ] AC-031 — 修改 order.payment.timeout-minutes 后新建订单按新值映射级别（动态生效无重启）
- [ ] AC-032 — 管理端可查三态任务、手动取消且留审计（后端 API 本 DU；页面 DU-FE-002）

## 执行顺序（Execution Order）

1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12 → 13

## 并行度（Parallelization）

无（任务 1~6 为发布链路基线；7~8 消费者依赖服务入口；9~12 兜底/管理端依赖前序；13 收口）

## Verification

- Unit: `mvn -pl mall-services/mall-order -am test`——Mapper/Policy/Assembler/Flusher/Handler/Scanner/Controller 全部 Mockito 单测
- Integration: H2+Flyway @SpringBootTest（V5 列断言、union 三源、delay_level 发送分支、findExpiredPending）
- API: DelayTaskAdminController 直调测试（DTO/筛选/404/409/审计）
- Migration: V5（mall-order delay_level）+ V14（mall-identity 权限菜单）
- Error Case: 订单不存在 skip+WARN；409 归并 skip；扫描器单条异常隔离；级别映射封顶/非法 WARN
