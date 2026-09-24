# DU Implementation — DU-BE-004

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

围绕"建单 Outbox 原子投递延迟检查事件 → RocketMQ 到期唤醒 → 回查订单当前状态 → 复用 M4 自动取消 → 定时兜底 + 管理端介入"主链路，落位 12 个 Task：

- **T1 存储层**：`V5__outbox_delay_level.sql` 加 `delay_level` 列；OutboxEvent/Po/Repository/Writer/Outbox 管理视图全链承载（null 归 0；Writer 新增四参 append，三参委托 level=0，均 `@Transactional(MANDATORY)`）。
- **T2 超时策略**：`PaymentTimeoutPolicy` 经 `SystemParameterProvider.getLong("order.payment.timeout-minutes", 默认)` 动态读取，缺键/非法回退默认。
- **T3 级别映射**：`DelayLevelMapper` 内置 RocketMQ 18 级秒表，按"首个 ≥ 目标秒数"向上对齐（30m→16）；支持 `mall.order.delay.force-level` 强制级别；越界封顶 18、非法回退 16 并 WARN。
- **T4 领域事件**：`OrderIntegrationEventType` 追加 PAYMENT_TIMEOUT_CHECK；`Order.create()` 在 ORDER_CREATED 后追加收集该事件。
- **T5 组装/落库**：`OrderEnvelopeAssembler` 构造器瘦身为 `(ObjectMapper)`，`assemble(order,event,timeoutMinutes)`；延迟事件 payload=`OrderDelayPayload(orderId,orderNo,createdAt+超时)`；`OutboxOrderEventFlusher` 注入 policy/mapper，一次 flush 只读一次超时，延迟事件四参 append 带级别。
- **T6 路由/投递**：EventRouter 默认注册 PAYMENT_TIMEOUT_CHECK→`aimall-order-delay`（级别占位 0）；OutboxDeliveryTask 发送分支读取行内 `delay_level` 走 sendDelay。
- **T7 取消服务**：抽出会员/系统共用的 `doCancel(order,operator,reason,reloader)`；新增 `systemCancel(orderId,reason,source)`（按主键加载不做归属，operator=`SYS:source`）；仓储端口补 `findById`。
- **T8 到期消费者**：`PaymentTimeoutCheckHandler`（`@ConditionalOnProperty rocketmq.enabled=true`）：订单不存在/非 PENDING→markSkipped；PENDING→systemCancel(...,PAYMENT_TIMEOUT,DELAY_MESSAGE)；STATUS_CONFLICT 归并 SKIPPED；其他异常上抛重试/DLQ。
- **T9 兜底扫描**：端口/Mapper 增加 `findExpiredPending(cutoff,limit)`（`SELECT id,order_no ... created_at < cutoff LIMIT`）；`OrderTimeoutFallbackScanner` 默认开启、60s 周期，逐单 systemCancel(...TIMEOUT_FALLBACK)，查询失败与单条异常均隔离。
- **T10 管理端**：`DelayTaskAdminMapper` 单条 SQL UNION ALL 三同构源（PENDING / PAYMENT_TIMEOUT 已取消 / outbox FAILED LEFT JOIN orders），动态筛选 + LIMIT/OFFSET + COUNT 包装；`DelayTaskAdminService` 编排（状态白名单、分页收口、`order-delay-audit` 审计）；`DelayTaskAdminController`（`/api/admin/order-delay/tasks` GET 分页、POST `{orderId}/cancel`）。
- **T11 权限菜单**：mall-identity `V14__order_delay_permissions.sql`：order-delay:list / order-delay:cancel 两权限、`/distributed/delay` 菜单页（DelayTaskList）、SUPER_ADMIN 授权（目录做存在性兜底，INSERT IGNORE）。
- **T12 配置**：mall-order application.yml 增加 `mall.order.delay.force-level`（ORDER_DELAY_FORCE_LEVEL）与 `mall.order.timeout-fallback.enabled/fixed-delay-ms` 环境变量注入。
- **T13 回归**：`mvn -pl mall-services/mall-order -am test` 全量 105 测试通过。

## Commits

基线 257df00（DU-BE-004 baseline）之后：

| Commit | Task |
| --- | --- |
| ebdfb1a | T1 outbox_event 加 delay_level 列并全链承载 |
| d5fa92c | T2 PaymentTimeoutPolicy 动态读取超时参数 |
| 9022c8f | T3 DelayLevelMapper 18 级向上对齐映射 |
| 369ebc9 | T4 建单收集 PAYMENT_TIMEOUT_CHECK 事件 |
| 52e7b1c | T5 assembler/flusher 支持延迟事件与同 flush 单超时 |
| 6d90dad | T6 延迟路由注册 + 投递按行内 delay_level sendDelay |
| 740b4ab | T7 抽出 doCancel，新增 systemCancel 系统取消入口 |
| 97b8665 | T8 PAYMENT_TIMEOUT_CHECK 到期回查消费者 |
| 3b7ff3d | T9 findExpiredPending + 超时兜底扫描器 |
| be4a55e | T10 union 三源延迟任务视图 + 管理端查询/人工取消/审计 |
| 61aba4d | T11 V14 延迟取消权限/菜单种子 |
| 04a905e | T12 延迟强制级别与兜底扫描配置 env 注入 |

（T13 为全量回归执行，无源码变更，无独立提交。）

## Deviations

无。

## 自检

- 红绿灯 TDD：各 Task 先补/改红灯测试再绿灯实现；T13 全量回归 105 通过、0 失败、0 错误。
- 幂等：MQ 层 consumed_event 占位由 AbstractIntegrationHandler 统一承载；业务层 systemCancel 经 CAS 保证延迟消息/兜底扫描/人工取消并发只产生一次真实取消。
- 双路径一致性：延迟消息与兜底扫描均收敛到同一 `systemCancel`（CAS + ORDER_CANCELLED Outbox + 库存释放链路）。
- 已知非本 DU 问题：mall-identity 全量测试存在 3 个既有失败（M1AuditAppendOnlyTest / InternalMemberSeedApiTest，经临时移除 V14 对照确认与本次变更无关，系仓库其他在途改动）。
