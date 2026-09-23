# DU Implementation — DU-BE-001

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

- **任务 1**（mall-bom）：`dependencyManagement` 新增 `rocketmq-spring-boot-starter:2.3.6` 版本权威声明（属性 `rocketmq-spring-boot-starter.version`），业务服务 pom 仅声明坐标不带版本。
- **任务 2**（mall-contracts/mall-event-contracts）：新增 `com.ai.mall.event` 包——`Envelope` 七字段契约（@JsonCreator + Builder 必填校验）、`EventTopics`/`EventTags`/`ConsumerGroups`/`EventVersions` 常量、`payload/` 5 个 record DTO（§41 四订单事件 + OrderDelayPayload）；`EventContractsSnapshotTest` 契约快照测试 5 用例。
- **任务 3**（mall-common/mall-common-mq）：`IntegrationEventProducer` 接口 + `RocketMQEnvelopeProducer` 实现——sendSync/sendAsync/sendDelay 三路发送；统一消息构造 Tag=eventType、keys=eventId、body=Envelope JSON；traceId 策略（显式值 > MDC > 自动生成）；sendDelay 校验 1~18 边界。
- **任务 4**（mall-common-mq）：`@IntegrationEventListener` 组合注解（topic/eventType/consumerGroup/maxSupportedVersion）+ `AbstractIntegrationHandler<P>` 六步处理链（版本裁决 WARN+ACK → traceId 写 MDC → 幂等占位 INSERT IGNORE → 业务 handle → 结果回写 → 消费日志+MDC 清理）；异常删占位 + RECONSUME_LATER 交 RocketMQ 重试。
- **任务 5**（mall-common-mq）：`IdempotentConsumer` 接口（TryResult/Result 枚举）+ `ConsumedEventRepository` JDBC 实现（INSERT IGNORE/UPDATE/DELETE，表名可配置默认 consumed_event，静态 DDL 供测试）+ `RocketMQProperties`（rocketmq.* 前缀）+ `RocketMQAutoConfiguration`（`@ConditionalOnProperty(rocketmq.enabled)` 全量开关；幂等组件 `@ConditionalOnBean(JdbcTemplate)` fail-closed；SmartInitializingSingleton 注册器扫描注解 handler 建 DefaultMQPushConsumer）+ `AutoConfiguration.imports` 注册。
- **任务 6**（测试基线，26 用例全绿）：
  - `EventContractsSnapshotTest` 5/5（TC-002/003）；
  - `RocketMQEnvelopeProducerTest` 5/5——消息构造契约/traceId 三级策略/delayLevel 边界（0/19 拒绝、1/18 打到消息 delayTimeLevel 属性）/发送失败上抛；
  - `AbstractIntegrationHandlerTest` 7/7——版本拒绝/重复跳过/成功回写/异常删占位/DB 异常 fail-closed/解析失败重试/MDC 清理；
  - `ConsumedEventRepositoryTest` 4/4（H2 MySQL 模式真实 INSERT IGNORE 语义：首占/重复/跨消费组隔离/回写/删占位重占）；
  - `RocketMQAutoConfigurationTest` 3/3（TC-007 切片：缺省不装配/enabled=true 装配齐全/无 JdbcTemplate 幂等 fail-closed）；
  - `RocketMQBrokerIntegrationTest` 7/7（TC-003~006 Testcontainers：apache/rocketmq:5.3.4 NameServer+Broker 真实容器——sendSync/sendAsync/sendDelay 均达、Tag/keys/七字段往返、MDC traceId 透传、v2 版本拒绝对照组、持续失败 3 次重试后进 %DLQ% 可查询、同 eventId 重复投递幂等跳过）。
- **任务 7**（回归收口）：`mvn -pl mall-common/mall-common-mq -am test` **26/26 全绿**（BUILD SUCCESS）；`mvn dependency:tree` 无 "omitted for conflict"（rocketmq-spring-boot-starter 2.3.6 → rocketmq-client 5.3.1/netty 4.1.135/fastjson2 与 Boot 3.5.15/SCA 2025.0.0.0 无冲突）；全仓 pom 扫描 RocketMQ 版本仅在 mall-bom 声明。

## Commits

| Commit | 任务 | 说明 |
| --- | --- | --- |
| 1c53407 | 任务 1 | feat(bom): mall-bom 新增 rocketmq-spring-boot-starter 2.3.6 版本权威声明 |
| e7c8598 | 任务 2 | feat(contracts): 新增集成事件契约 event 包（Envelope/常量/Payload DTO） |
| 963a28c | 任务 3 | feat(mq): RocketMQEnvelopeProducer 三路发送 |
| 68fcdc8 | 任务 4+5 | feat(mq): 集成事件消费链与开关装配（任务 4/5 文件耦合——AbstractIntegrationHandler 依赖 IdempotentConsumer——合并提交保证可编译） |
| ebdbbd9 | 任务 6 | test(mq): TC-002~009 测试基线 |

- 任务 7（回归收口）无独立代码变更，验证证据见 evidence/。均为本地提交，未推送。

## Deviations

### DEV-1
- 原 DU 建议: requirement-design §2.1 将 Envelope/Topic/Tag 常量/Payload DTO 列在 mall-mq 模块结构内。
- 实际实现: 事件契约整体落位 mall-contracts/mall-event-contracts（纯 POJO 模块），mall-mq 仅依赖不定义。
- 原因: story-design.md 定稿裁决——契约归 contracts 世界，业务服务与 ai-service 都可零 Spring 依赖引用。
- 影响评估: 与 task-spec 任务 2 落点一致，无功能影响；mall-mq 保持纯运行时组件。

### DEV-2
- 原 DU 建议: task-design sketch 假设 `producer.send(message, delayLevel)` 直传延迟级别。
- 实际实现: `message.setDelayTimeLevel(delayLevel)` + `producer.send(message)`。
- 原因: RocketMQ 客户端无 `send(Message, int delayLevel)` 重载——原写法被编译器宽化为 `send(Message, long timeout)`，延迟级别会被当成发送超时毫秒数（单测红→绿真实拦截的缺陷）。
- 影响评估: 修复后延迟语义正确（IT 验证 delayLevel=1 延迟投递成功）；无接口变化。

### DEV-3
- 原 DU 建议: 消费者可由 rocketmq-spring-boot-starter `@RocketMQMessageListener` 自动装配。
- 实际实现: 自建 `DefaultMQPushConsumer`——SmartInitializingSingleton 注册器扫描 `@IntegrationEventListener` handler Bean 手动装配。
- 原因: starter 监听器容器不支持 `rocketmq.enabled` 单开关整体豁免与 `@ConditionalOnBean(JdbcTemplate)` 幂等 fail-closed 装配语义。
- 影响评估: 换取"开关=false 时零 Bean 无半开态"（AC-008）与幂等组件缺失即拒绝消费的设计约束；starter 仍作为客户端依赖引入。

### DEV-4
- 原 DU 建议: task-spec 任务 4 "异常删占位上抛重试" 表述为异常向上传播。
- 实际实现: handle 异常在 `processMessage` 内捕获——删占位→记 ERROR 日志→返回 `RECONSUME_LATER` 交 RocketMQ 客户端重试（超限进 DLQ）。
- 原因: 消费回调不能向 broker 抛异常，重试语义由返回状态码表达（适配 Push 消费模型）。
- 影响评估: 行为等价（重试+占位回滚+超限 DLQ），单测与 IT 双重验证。

### DEV-5
- 原 DU 建议: story-design 未约定 Testcontainers 端口策略。
- 实际实现: IT broker 用 `listenPort=20911` + 固定端口映射（20909/20911/20912），brokerIP1=127.0.0.1。
- 原因: broker 向 namesrv 广播 brokerIP1:listenPort，宿主机 client 直连该地址；避开 repo-4 dev compose 栈占用的 10909~10912。
- 影响评估: IT 与本地 compose 栈可并行运行；无生产影响。

### DEV-6
- 原 DU 建议: requirement-design §2.1 模块树写 `mall-common/mall-mq/`。
- 实际实现: 模块落位 `mall-common/mall-common-mq/`（artifactId=mall-common-mq）。
- 原因: 对齐 mall-common 聚合层既有兄弟模块命名（mall-common-core/web/config/redis/security/openfeign/log/test），保持同一命名口径。
- 影响评估: 仅模块坐标差异，包名 com.ai.mall.common.mq 不变，无功能影响；sdd-review 阶段补记。

## 自检

- [x] task-spec.md 任务 1~7 全部完成，范围未超出权威表 DU-BE-001 scope
- [x] AC-002/004/005/006/007/008/009 由对应测试用例直接验证（见 evidence/ EV-001~EV-003）
- [x] AC-003：Topic/Tag/消费者组常量仅在 mall-contracts 声明，mall-mq 全部引用常量（grep 无散落硬编码）
- [x] AC-041：`mvn dependency:tree` 0 冲突；全仓 pom 扫描 rocketmq 版本仅在 mall-bom（EV-004）
- [x] `mvn -pl mall-common/mall-common-mq -am test` 26/26 全绿（BUILD SUCCESS）
- [x] 六步链语义（版本裁决/MDC/幂等/回写/日志清理/DLQ）单测+真实 broker 双覆盖
- [x] evidence：EV-001~EV-005 记录于 evidence/evidence.yaml
