# DU Task Design — DU-BE-001

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-001 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-001 与同目录 task-spec.md（"做什么/验收"）配对互链。
> 本文件固定为 Expected Implementation（Plan / Sketch / Pseudocode），
> 与 implementation.md（Actual Implementation）分立，不得合并。

## 1. Goal

repo-1 落地 M7 事件底座：mall-bom 统一 RocketMQ 客户端版本；mall-contracts 新增 event 包（Envelope 七字段/Topic/Tag/消费者组/版本矩阵/Payload DTO 集中声明）；mall-mq 子模块实现生产者三态发送、组合注解消费者抽象与统一处理链（版本拒绝/TraceId/幂等挂点/日志/重试交接）、IdempotentConsumer 组件与 `rocketmq.enabled` 开关装配；配套单测 + Testcontainers 集成测试（story-design.md §5 DU-BE-001 职责）。

## 2. Repository

repo-1（implementation/ai-platform-backend：mall-bom、mall-contracts、mall-common/mall-mq）。

## 3. Scope

- mall-bom：dependencyManagement 新增 `org.apache.rocketmq:rocketmq-spring-boot-starter:2.3.x`（精确版本 dev 首任务锁定）
- mall-contracts 新增 `event` 包（纯 POJO/常量，零 Spring 依赖）：`Envelope`、`EventTopics`（aimall-order-events/aimall-order-delay/aimall-inventory-events）、`EventTags`（5 个 Tag 常量）、`ConsumerGroups`（inventory-consumer-group/order-delay-consumer-group）、`EventVersions`（v1 矩阵）、Payload DTO×5（§41 四订单事件 + OrderDelayPayload）
- mall-mq：`RocketMQAutoConfiguration`（@ConditionalOnProperty 开关装配）、`IntegrationEventProducer` 接口 + `RocketMQEnvelopeProducer`（sendSync/sendAsync/sendDelay）、`@IntegrationEventListener` 组合注解 + `AbstractIntegrationHandler<E>` 模板（六步处理链）、`IdempotentConsumer` 接口 + `ConsumedEventRepository` JDBC 实现（INSERT IGNORE/UPDATE/DELETE，表名可配置默认 consumed_event）、`RocketMQProperties`
- 测试：mall-mq 单测 + Testcontainers 集成测试（apache/rocketmq 容器）+ mall-contracts 契约快照测试

## 4. Design References

- requirement-design.md §2.1（mall-mq 子模块/mall-contracts event 包）、§2.0（十二条总体策略：全量事件走 Outbox/退避/幂等先占位/版本拒绝 ACK+WARN）、§4（Event Contract：Envelope 七字段与 §41 Payload）、§5 数据变更（本 DU 无业务库迁移，IdempotentConsumer 以接口+可配置 SQL 先行）
- story-design.md §1 repo-1 节（实现明细）、§2（接口契约细化：Tag=eventType/keys=eventId/失败删占位）、§6（TC-002~008 测试策略）、§7（待 dev 确认项）

## 5. Dependencies

DU-INFRA-001（权威表 depends on：DU-INFRA-001；连接与冒烟依赖 broker 就绪，Testcontainers 场景自起容器不依赖 compose 环境）。

## 6. Implementation Sketch

```
mall-bom/pom.xml（唯一版本权威）
  dependencyManagement += org.apache.rocketmq:rocketmq-spring-boot-starter（2.3.x 精确版本）
  业务服务 pom 仅 groupId/artifactId 不带版本

mall-contracts/event（纯 POJO，零 Spring 依赖，供生产/消费两侧共享）
  Envelope {eventId, eventType, eventVersion, occurredAt, producer, traceId, payload:JsonNode}
    └── build 工厂 + Jackson 序列化（payload 用 JsonNode 保持 schema 自由）
  EventTopics / EventTags / ConsumerGroups / EventVersions（常量，禁止业务侧硬编码）
  payload DTO×5（字段与 requirement-design §4 Event Contract 逐一对齐，快照测试锁定）

mall-mq（组合到 mall-common 多模块）
  RocketMQAutoConfiguration（@ConditionalOnProperty(name="rocketmq.enabled", havingValue="true")）
    ├── RocketMQEnvelopeProducer（实现 IntegrationEventProducer）
    ├── ListenerContainer 装配（扫描 @IntegrationEventListener Bean）
    └── false → 上述 Bean 全部不装配，服务正常启动（无 NoOp 半开态，开关语义由业务侧裁决）
  RocketMQEnvelopeProducer
    ├── sendSync(TopicTag, Envelope) → SendResult（异常上抛，不吞）
    ├── sendAsync(TopicTag, Envelope, SendCallback)
    └── sendDelay(TopicTag, Envelope, delayLevel)（延迟级别向上取整，topic=aimall-order-delay）
    消息构造：topic=前缀+Topic 常量 / tag=eventType / keys=eventId / body=Envelope JSON
    traceId：从 MDC 读取当前值注入 Envelope（缺失自动生成，不阻断）
  AbstractIntegrationHandler<E>（@IntegrationEventListener(topic/tag/consumerGroup/eventType/maxSupportedVersion)）
    处理链六步（见 §7 Pseudocode）
  IdempotentConsumer（接口）→ ConsumedEventRepository（JDBC：INSERT IGNORE 占位 / UPDATE result / DELETE 回滚占位；表名可配置，默认 consumed_event）

错误处理路径：sendSync 失败上抛（DU-BE-002 投递任务捕获退避）；消费 handle 抛异常 → 先删幂等占位 → 异常上抛交 starter 重试（maxReconsumeTimes）→ 超限 DLQ；框架层只记 ERROR（eventId/traceId/重试次数）不中断容器。
```

## 7. Pseudocode

命中 complexity-trigger（business-flow：消费统一处理链含版本裁决/幂等占位/异常回滚多分支）——覆盖主流程与关键异常分支：

```
function onMessage(message):                      # AbstractIntegrationHandler 统一入口
  envelope = parse(message.body)                  # Envelope JSON → 对象；解析失败 → 异常上抛（进重试）
  if envelope.eventVersion > maxSupportedVersion: # ① 版本裁决
      WARN("version rejected", eventId, envelope.eventVersion)
      return ACK                                  # 拒绝但不重试（避免死循环），不按旧版本解析
  traceId = envelope.traceId ?? newTraceId()      # ② TraceId：缺失生成新 ID，不阻断
  MDC.put("traceId", traceId)
  try:
      result = idempotentConsumer.tryConsume(envelope.eventId)   # ③ 幂等占位（INSERT IGNORE）
      if result == DUPLICATE:
          INFO("duplicate skipped", eventId); return ACK        # 重复消息直接 ACK
      payload = deserialize(envelope.payload)
      handle(payload)                              # ④ 子类业务处理
      idempotentConsumer.markResult(envelope.eventId, SUCCESS)   # ⑤ 结果回写
      return ACK
  catch e:
      idempotentConsumer.deletePlaceholder(envelope.eventId)     # 异常：删除占位（允许重试再处理）
      ERROR("consume failed", eventId, traceId, retryCount)      # 只记日志，不吞
      throw e                                     # 上抛交 starter 重试 → 超限 DLQ
  finally:
      MDC.clear()                                 # ⑥ 消费日志（eventId/耗时/result）+ 清理 MDC

function sendDelay(topicTag, envelope, delayLevel):              # 生产者延迟三态之一
  msg = new Message(prefix + topicTag.topic, envelope.eventType,  # tag=eventType
                    envelope.eventId,                             # keys=eventId
                    toJson(envelope))                             # body=Envelope JSON
  if delayLevel 不在支持区间: throw IllegalArgumentException       # 参数校验，不静默修正
  return producer.send(msg, delayLevel)            # 延迟级别向上取整；失败上抛（Outbox 任务退避）
```

边界与异常分支：幂等占位表不可用（DB 异常）→ 异常上抛进重试（fail-closed，不降级为无幂等消费）；SKIPPED 由 handler 显式 markSkipped 回写；解析失败与版本拒绝区分（前者重试，后者 ACK+WARN）。
