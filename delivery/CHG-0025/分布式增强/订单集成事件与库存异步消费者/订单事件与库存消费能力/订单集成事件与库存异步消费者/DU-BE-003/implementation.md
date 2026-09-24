# DU Implementation — DU-BE-003

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。

## 变更内容

- **任务 1**（领域事件）：`domain/order/event/OrderIntegrationEventType`（ORDER_CREATED/PAYMENT_SUCCEEDED/ORDER_CANCELLED/ORDER_COMPLETED，领域不反向依赖契约包）、`OrderIntegrationEvent` record（仅类型标记，payload 事务 flush 时从聚合读取）；Order 聚合新增 `integrationEvents` 列表，create/pay/cancel/confirmReceipt 四方法追加事件，`reconstitute` 初始化为空，新增 `pullIntegrationEvents()`（取出并清空）。
- **任务 2**（信封组装器）：`application/order/event/OrderEnvelopeAssembler`——按事件类型构造 §41 Payload（reservationNo=orderNo；paymentNo="PAY"+orderNo；金额 money.payFen/CNY；paymentDeadline=createdAt+order.payment.timeout-minutes 默认 30）并组装 Envelope（UUID/eventVersion=1/producer=mall-order/traceId=TraceContext.get）；`toTag()` 静态映射领域枚举到 EventTags。
- **任务 3**（Flusher + 模式判定）：`OrderEventOutbox` 端口 `flush(Order)`、`OutboxOrderEventFlusher`（async：逐事件 assembler→`outboxRecordWriter.append(aggregateId=orderId, tag, envelope)`，MANDATORY 同事务；sync：事件取出丢弃）、`IntegrationMode`（`@Value("${rocketmq.enabled:false}")`，单开关权威来源）。
- **任务 4**（仓储挂点）：`MyBatisOrderRepository` 构造器新增 OrderEventOutbox；`insert` 在 id 回填+行+历史落库后 flush（ORDER_CREATED payload 需 orderId）；`transition` 仅 rows==1 插 history 后 flush，CAS 落败不 flush；`StatusTransition` record 新增 `order` 字段承载聚合；新增 `findStatusById(orderId)`（仅取状态不重建整聚合）。
- **任务 5**（服务分支降级）：PaymentService/OrderCancelService 注入 IntegrationMode——async CAS 赢后直接返回，不调同步 confirm/release；sync 保留既有 confirmAfterPaid/releaseAfterCancel 私有方法并打 WARN 降级日志。OrderCreateService（lock 仍同步）/ReceiptService 无需分支。
- **任务 6**（internal 端点）：`OrderStatusInternalController` GET /api/internal/orders/{orderId}/status → {orderId,status}，不存在 404；`CompensationInternalController` POST /api/internal/compensations，按 type=INVENTORY_CONFIRM_DEDUCT|INVENTORY_RELEASE 调既有 OrderCompensationPort 幂等登记。两侧均经既有安全链 ROLE_SERVICE + X-Internal-Token 保护。
- **任务 7**（库存基础设施）：mall-inventory pom 新增 mall-common-mq + mall-event-contracts（显式 ${project.version}）；仓储新增 `findReservationIdsByOrderNo(orderNo)`（Mapper `@Select ... LIKE orderNo || ':%'`）；`infrastructure/client/OrderServiceClient`（RestClient，baseUri=mall.inventory.order-uri 默认 http://localhost:8105，X-Internal-Token + Trace 拦截器），`getStatus(orderId)`（异常传播触发重试）、`registerCompensation(request)`。
- **任务 8**（两个消费者）：`PaymentSucceededInventoryHandler`——回查状态：CANCELLED markSkipped+WARN；否则枚举 orderNo:% 逐行 `confirmDeduction(ConfirmCommand)`（DEDUCTED 幂等返回）；异常先登记 INVENTORY_CONFIRM_DEDUCT 补偿再抛出。`OrderCancelledInventoryHandler`——CANCELLED 逐行 release（RELEASED 幂等）；PAID/SHIPPED/COMPLETED markSkipped 防误释放；状态未知保守抛错；异常登记 INVENTORY_RELEASE 后抛出。两 Handler 均 @ConditionalOnProperty(rocketmq.enabled=true) + @IntegrationEventListener(topic=aimall-order-events, group=inventory-consumer-group, maxVersion=1)。
- **任务 9**（配置）：mall-order application.yml 新增 rocketmq 段（enabled/name-server/producer-group 环境变量化，默认关）与 mall.order.payment.timeout-minutes；mall-inventory application.yml 新增 rocketmq 段与 mall.inventory.order-uri。
- **任务 10**（全量回归）：`mvn -pl mall-services/mall-order,mall-services/mall-inventory -am test`——**mall-order 70/70、mall-inventory 37/37 全绿**，M4/M5 既有链路零回退。
- **延后说明**：consumed_event 建表与消费幂等通用化按设计归 STORY-009-05-01；本 Story Handler 单测 mock IdempotentConsumer，运行时由 mall-common-mq 自动装配。AC-019/020/022 的真实跨服务运行态证据在 Change 级 converge 产出。

## Commits

| Commit | 任务 | 说明 |
| --- | --- | --- |
| 31f3b6c | 任务 1 | 订单聚合集成事件收集与 pull |
| 6d59155 | 任务 2 | OrderEnvelopeAssembler 四类订单事件信封组装 |
| 2dca669 | 任务 3 | OutboxOrderEventFlusher 事务内 flush + IntegrationMode |
| 1482141 | 任务 4 | 仓储 insert/transition 事务内 flush + findStatusById |
| 967e359 | 任务 5 | 支付/取消异步分支与 MQ 关闭同步降级 |
| 317eb15 | 任务 6 | 订单状态回查与补偿登记 internal 端点 |
| 94960dc | 任务 7 | 库存 pom+按订单号枚举预留+OrderServiceClient |
| ae7cd00 | 任务 8 | PAYMENT_SUCCEEDED/ORDER_CANCELLED 库存消费者 |
| 16c506d | 任务 9 | 两侧 rocketmq 段与 order-uri/timeout-minutes 配置 |

- 本地提交，未推送。

## Deviations

### DEV-1
- 原 DU 建议: event/ 包路径放 application/order/event 下。
- 实际实现: 事件类型与事件记录放 domain/order/event（纯领域元素），application/order/event 只放 flusher/assembler/mode。
- 原因: 事件由聚合根在状态迁移中收集，属领域职责；分层更清晰，包引用方向不变。
- 影响评估: 无功能影响，task-spec 任务 1 路径以实际为准。

### DEV-2
- 原 DU 建议: mall-event-contracts 版本经 mall-bom 托管。
- 实际实现: mall-inventory pom 显式声明 ${project.version}。
- 原因: mall-bom 未管理该 artifact（mall-common-mq 同样显式声明），不写版本 Maven 无法读 POM。
- 影响评估: 与既有模块口径一致；无功能影响。

### DEV-3
- 原 DU 建议: ORDER_CANCELLED 消费仅裁决 CANCELLED 与其他两类。
- 实际实现: 真实状态非 CANCELLED 且不在 PAID/SHIPPED/COMPLETED（null/未知）时保守抛错交重试，而非 markSkipped。
- 原因: 状态未知可能只是回查暂时失败，直接 ACK 有丢消息风险；重试至超限 DLQ 由 STORY-009-05-01 补偿闭环兜底。
- 影响评估: 满足 AC-023 失败安全语义；正常路径行为不变。
