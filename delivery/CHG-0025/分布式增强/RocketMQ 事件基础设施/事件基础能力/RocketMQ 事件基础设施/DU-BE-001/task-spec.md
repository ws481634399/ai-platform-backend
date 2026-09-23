# DU Task Spec — DU-BE-001

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-001 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-001 与同目录 task-design.md（"怎么做"）配对互链。
> verifies 约定：任务项绑定 TC-NNN（红绿灯对象）；TC 定义在外部对应 Story 的 test-design.md，本文件只引用不新造。

## 0. 元信息

- DU id: DU-BE-001
- Change ID: CHG-0025
- Feature Path: 分布式增强/RocketMQ 事件基础设施/事件基础能力/RocketMQ 事件基础设施
- 权威来源: story-design.md §5 / DU-BE-001

## 任务清单

<!-- 每行一个 - [ ] 复选框任务，带 verifies: TC-NNN；约束：任务范围不得超出权威表本 DU 的 scope。 -->

- [ ] 任务 1 — mall-bom dependencyManagement 新增 rocketmq-spring-boot-starter 2.3.x 精确版本（与 Spring Boot 3.5.15/Spring Cloud Alibaba 2025.0.0.0 兼容矩阵冒烟）；各业务服务 pom 声明依赖不带版本号（verifies: TC-008）
- [ ] 任务 2 — mall-contracts 新增 event 包：Envelope 七字段（build 工厂+Jackson 序列化）、EventTopics/EventTags/ConsumerGroups/EventVersions 常量、Payload DTO×5（§41 四订单事件+OrderDelayPayload，字段对齐 requirement-design §4）（verifies: TC-002）
- [ ] 任务 3 — mall-mq 实现 IntegrationEventProducer + RocketMQEnvelopeProducer（sendSync/sendAsync/sendDelay；topic 前缀/tag=eventType/keys=eventId/body=Envelope JSON；traceId 从 MDC 注入缺失自动生成）（verifies: TC-003）
- [ ] 任务 4 — mall-mq 实现 @IntegrationEventListener 组合注解 + AbstractIntegrationHandler 六步处理链（版本裁决→MDC→幂等占位→handle→结果回写→日志；异常删占位上抛重试）（verifies: TC-004, TC-005, TC-006）
- [ ] 任务 5 — mall-mq 实现 IdempotentConsumer 接口 + ConsumedEventRepository JDBC 实现（INSERT IGNORE 占位/UPDATE result/DELETE 回滚；表名可配置默认 consumed_event）+ RocketMQProperties + RocketMQAutoConfiguration 开关装配（verifies: TC-007）
- [ ] 任务 6 — 测试基线：mall-contracts 契约快照单测；mall-mq 单测（Envelope/版本拒绝/退避参数纯逻辑）；Testcontainers 集成测试（三种发送/MDC 透传/v2 拒绝/持续失败 DLQ/keys 断言）（verifies: TC-002~TC-006, TC-009）
- [ ] 任务 7 — 回归收口：mall-mq + mall-contracts `mvn test` 全绿，依赖树无冲突（mvn dependency:tree），作为 Story 1 完成基线（verifies: TC-009）

## Acceptance Criteria

<!-- 须完整覆盖权威表本 DU covers 列出的 AC-NNN（AC 定义在 requirement-spec.md §5）。 -->

- [ ] AC-001 — mall-order/mall-inventory 引入 mall-mq 依赖后经 Nacos 下发 `rocketmq.name-server` 地址可连通 broker（Testcontainers/集成测试佐证；compose 运行态连通由 Integration Gate 复核）
- [ ] AC-002 — 发送事件后消息到达指定 Topic/Tag；Envelope 七字段齐全非空，traceId 缺失时自动生成（集成测试断言消息体）
- [ ] AC-003 — Topic/Tag/消费者组常量仅在 mall-contracts 声明；全仓 grep 扫描业务服务无散落硬编码（快照测试+扫描证据）
- [ ] AC-004 — sendSync/sendAsync/sendDelay 三种发送路径集成测试均达；delayLevel 参数化生效（延迟级别向上取整）
- [ ] AC-005 — 消费端 MDC.traceId == Envelope.traceId 且传播至下游 internal 调用日志；缺失 traceId 自动生成新 ID 且消费不阻断
- [ ] AC-006 — 构造 eventVersion=v2 事件 → WARN+ACK 拒绝，handler 未执行，不按旧版本解析
- [ ] AC-007 — 持续失败消息按 RocketMQ 策略重试，超限进 DLQ；DLQ 消息可经查询入口查到，不静默
- [ ] AC-008 — rocketmq.enabled=false → Producer/Listener Bean 不装配、调用方同步路径可用、服务正常启动无半开；true → 装配齐全收发恢复
- [ ] AC-009 — mall-common mq 子模块单测/集成测试全绿（Envelope 构建/发送/消费/TraceId/版本拒绝）
- [ ] AC-041 — RocketMQ 客户端版本仅在 mall-bom 管理；各服务 pom 无散落版本号；dependency:tree 与 Spring Boot 3.5.15/Spring Cloud Alibaba 2025.0.0.0 无冲突

## 执行顺序（Execution Order）

<!-- 任务执行先后顺序；跨 DU 顺序须服从权威表 depends on，不得抢跑。 -->

1. 任务 1（版本权威就绪）→ 2. 任务 2（契约先行）→ 3. 任务 3 → 4. 任务 4 → 5. 任务 5 → 6. 任务 6 → 7. 任务 7（回归收口）

## 并行度（Parallelization）

<!-- 可并行的任务分组（可空，写"无"表示全串行）；不得违反权威表依赖图（无环）。 -->

- 任务 3 与任务 5 可并行（生产者与幂等组件互不依赖，均依赖任务 1/2）
- 其余串行：任务 4 依赖任务 3/5（处理链调用生产者无关但依赖幂等组件），任务 6 依赖 3/4/5，任务 7 最后

## Verification

<!-- 必填：逐项给出验证方式与证据位置（测试文件/命令/报告路径）。 -->

- Unit: mall-contracts 契约快照测试（Envelope 七字段/常量齐备/DTO 对齐 §41）；mall-mq 纯逻辑单测（版本拒绝判定/延迟参数校验/退避参数）。命令：`mvn -pl mall-contracts,mall-common/mall-mq test`；证据：surefire 报告
- Integration: mall-mq Testcontainers（apache/rocketmq 5.x 容器）：三种发送/MDC 透传/v2 拒绝/持续失败 DLQ/keys=eventId 断言；对应 TC-003~TC-006；Docker 不可用时降级 compose 环境驱动（story-design §7）
- API: N/A（本 DU 无对外 HTTP 端点；消息契约由 Envelope/快照测试锁定）
- Migration: N/A（consumed_event 表随 DU-BE-005 建表；本 DU 集成测试用 Testcontainers 自建表）
- Error Case: 消费 handle 抛异常 → 占位删除+异常上抛+starter 重试（TC-006）；幂等表 DB 异常 → fail-closed 上抛不降级；sendSync 失败上抛由调用方（DU-BE-002）退避处理（契约测试断言异常类型）
