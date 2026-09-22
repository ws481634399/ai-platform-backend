package com.ai.mall.common.mq.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.ai.mall.common.mq.producer.IntegrationEventProducer;
import com.ai.mall.common.mq.producer.RocketMQEnvelopeProducer;
import com.ai.mall.event.Envelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 真实 broker 集成测试（TC-003~TC-006）：Testcontainers 启动 apache/rocketmq 5.3.4
 * NameServer + Broker（镜像同 repo-4 compose 定稿版本），验证三种发送路径、
 * 消息契约（Tag=eventType/keys=eventId/body=Envelope JSON）、MDC traceId 透传、
 * v2 版本拒绝、持续失败重试后进 DLQ、以及真实 SQL 幂等跳过（H2 MySQL 模式）。
 * <p>端口说明：brokerIP1=127.0.0.1 + listenPort=20911（20909/20912 为 VIP/HA 口），
 * 宿主机 client 直连 127.0.0.1:20911，避开 repo-4 dev compose 栈的 10909~10912。</p>
 */
@Testcontainers
class RocketMQBrokerIntegrationTest {

    private static final String TOPIC_MAIN = "aimall-it-topic";
    private static final String TOPIC_FAIL = "aimall-it-fail-topic";
    private static final String DLQ_TOPIC = "%DLQ%it-failing-group";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final Network NETWORK = Network.newNetwork();

    private static final String BROKER_CONF = """
            brokerClusterName = aimall-default
            brokerName = broker-a
            brokerId = 0
            brokerIP1 = 127.0.0.1
            listenPort = 20911
            namesrvAddr = rocketmq-namesrv:9876
            deleteWhen = 04
            fileReservedTime = 48
            brokerRole = ASYNC_MASTER
            flushDiskType = ASYNC_FLUSH
            autoCreateTopicEnable = true
            """;

    @Container
    static final GenericContainer<?> NAME_SRV = new GenericContainer<>("apache/rocketmq:5.3.4")
            .withNetwork(NETWORK)
            .withNetworkAliases("rocketmq-namesrv")
            .withExposedPorts(9876)
            .withEnv("JAVA_OPT_EXT", "-Xms256m -Xmx256m -Xmn128m")
            .withCommand("./mqnamesrv")
            .waitingFor(Wait.forLogMessage(".*Name Server boot success.*", 1))
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    static final GenericContainer<?> BROKER = new GenericContainer<>("apache/rocketmq:5.3.4")
            .withNetwork(NETWORK)
            .withNetworkAliases("rocketmq-broker")
            .dependsOn(NAME_SRV)
            .withEnv("JAVA_OPT_EXT", "-Xms512m -Xmx512m -Xmn256m")
            .withCopyToContainer(Transferable.of(BROKER_CONF), "/home/rocketmq/broker.conf")
            .withCommand("./mqbroker", "-c", "/home/rocketmq/broker.conf")
            // 固定端口映射：broker 向 namesrv 广播 brokerIP1:listenPort（127.0.0.1:20911），
            // 宿主机 client 直连该地址，需把 20909~20912 三口固定绑到宿主机同号端口
            .withCreateContainerCmdModifier(cmd -> cmd.withPortBindings(List.of(
                    new PortBinding(Ports.Binding.bindPort(20909), new ExposedPort(20909)),
                    new PortBinding(Ports.Binding.bindPort(20911), new ExposedPort(20911)),
                    new PortBinding(Ports.Binding.bindPort(20912), new ExposedPort(20912)))))
            .waitingFor(Wait.forLogMessage(".*boot success.*", 1))
            .withStartupTimeout(Duration.ofMinutes(3));

    private static String nameServerAddr;
    private static DefaultMQProducer rawProducer;
    private static IntegrationEventProducer eventProducer;
    private static final List<DefaultMQPushConsumer> consumers = new CopyOnWriteArrayList<>();

    private static HappyHandler happy;
    private static V2AcceptingHandler v2Accepting;
    private static FailingHandler failing;
    private static final List<MessageExt> deliveredHappy = new CopyOnWriteArrayList<>();
    private static final List<MessageExt> deliveredV2 = new CopyOnWriteArrayList<>();
    private static final List<String> dlqBodies = new CopyOnWriteArrayList<>();

    /** 正常消费组：maxSupportedVersion=1（v2 事件应被拒绝），幂等走真实 H2 表 */
    @IntegrationEventListener(topic = TOPIC_MAIN, eventType = "IT_EVENT",
            consumerGroup = "it-happy-group", maxSupportedVersion = 1)
    static final class HappyHandler extends AbstractIntegrationHandler<JsonNode> {

        final List<Envelope> handled = new CopyOnWriteArrayList<>();
        final List<String> observedTraceIds = new CopyOnWriteArrayList<>();

        HappyHandler(ObjectMapper mapper, IdempotentConsumer idempotent) {
            super(mapper, idempotent);
        }

        @Override
        protected void handle(Envelope envelope, JsonNode payload) {
            // 记录处理期间 MDC traceId，验证②步链路与 Envelope.traceId 一致
            observedTraceIds.add(MDC.get(MDC_TRACE_KEY));
            handled.add(envelope);
        }
    }

    /** 宽松版本消费组：maxSupportedVersion=2，用于证明 v2 事件确实送达 broker（对照组） */
    @IntegrationEventListener(topic = TOPIC_MAIN, eventType = "IT_EVENT",
            consumerGroup = "it-v2-group", maxSupportedVersion = 2)
    static final class V2AcceptingHandler extends AbstractIntegrationHandler<JsonNode> {

        final List<Envelope> handled = new CopyOnWriteArrayList<>();

        V2AcceptingHandler(ObjectMapper mapper, IdempotentConsumer idempotent) {
            super(mapper, idempotent);
        }

        @Override
        protected void handle(Envelope envelope, JsonNode payload) {
            handled.add(envelope);
        }
    }

    /** 持续失败消费组：验证删占位重试与超限进 DLQ（AC-007） */
    @IntegrationEventListener(topic = TOPIC_FAIL, eventType = "FAIL_EVENT",
            consumerGroup = "it-failing-group", maxSupportedVersion = 1)
    static final class FailingHandler extends AbstractIntegrationHandler<JsonNode> {

        final List<String> attempts = new CopyOnWriteArrayList<>();

        FailingHandler(ObjectMapper mapper, IdempotentConsumer idempotent) {
            super(mapper, idempotent);
        }

        @Override
        protected void handle(Envelope envelope, JsonNode payload) {
            attempts.add(envelope.getEventId());
            throw new IllegalStateException("持续失败：验证重试与 DLQ");
        }
    }

    @BeforeAll
    static void startAll() throws Exception {
        nameServerAddr = "127.0.0.1:" + NAME_SRV.getMappedPort(9876);

        // 预建 Topic/DLQ：消除 autoCreate + 消费组再平衡（20s）的启动竞态
        createTopic(TOPIC_MAIN);
        createTopic(TOPIC_FAIL);
        createTopic(DLQ_TOPIC);

        // H2 MySQL 兼容模式承载 consumed_event（真实 INSERT IGNORE 占位语义）
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:it_consumed_event;MODE=MySQL;DB_CLOSE_DELAY=-1"));
        jdbcTemplate.execute(ConsumedEventRepository.ddl("consumed_event"));
        ConsumedEventRepository repository = new ConsumedEventRepository(jdbcTemplate, "consumed_event");

        rawProducer = new DefaultMQProducer("it-producer-group");
        rawProducer.setNamesrvAddr(nameServerAddr);
        rawProducer.setSendMsgTimeout(5000);
        rawProducer.start();
        eventProducer = new RocketMQEnvelopeProducer(rawProducer, MAPPER);

        happy = new HappyHandler(MAPPER, repository);
        v2Accepting = new V2AcceptingHandler(MAPPER, repository);
        failing = new FailingHandler(MAPPER, repository);
        startConsumer("it-happy-group", TOPIC_MAIN, "IT_EVENT", happy, 16, deliveredHappy);
        startConsumer("it-v2-group", TOPIC_MAIN, "IT_EVENT", v2Accepting, 16, deliveredV2);
        // 失败组最大重试 2 次（初始 + 2 重试 = 3 次处理后进 DLQ）
        startConsumer("it-failing-group", TOPIC_FAIL, "FAIL_EVENT", failing, 2, new CopyOnWriteArrayList<>());

        // DLQ 查询入口消费组（模拟 mall-admin 补偿页的 RocketMQ Dashboard 查询能力）
        DefaultMQPushConsumer dlqConsumer = new DefaultMQPushConsumer("it-dlq-group");
        dlqConsumer.setNamesrvAddr(nameServerAddr);
        dlqConsumer.setInstanceName("it-dlq-group");
        dlqConsumer.subscribe(DLQ_TOPIC, "*");
        dlqConsumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt messageExt : msgs) {
                dlqBodies.add(new String(messageExt.getBody(), StandardCharsets.UTF_8));
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        dlqConsumer.start();
        consumers.add(dlqConsumer);
    }

    @AfterAll
    static void stopAll() {
        consumers.forEach(DefaultMQPushConsumer::shutdown);
        if (rawProducer != null) {
            rawProducer.shutdown();
        }
    }

    private static void startConsumer(String group, String topic, String tag,
                                      InternalHandlerAdapter adapter, int maxReconsumeTimes,
                                      List<MessageExt> delivered) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(nameServerAddr);
        consumer.setInstanceName(group + "-it");
        consumer.setMaxReconsumeTimes(maxReconsumeTimes);
        consumer.subscribe(topic, tag);
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            // 记录所有投递（含被幂等/版本裁决跳过的消息），供 keys/重复断言
            delivered.addAll(msgs);
            // 重试延迟降为 1s（delayLevel=1），避免默认 10s 拖慢用例
            context.setDelayLevelWhenNextConsume(1);
            return adapter.processBatch(msgs, context);
        });
        consumer.start();
        consumers.add(consumer);
    }

    private static void createTopic(String topic) throws Exception {
        for (int i = 0; i < 15; i++) {
            try {
                // ContainerState.execInContainer（返回 Container.ExecResult）；broker 刚启动注册未完成时可能失败，退避重试
                var result = BROKER.execInContainer("./mqadmin", "updateTopic",
                        "-n", "rocketmq-namesrv:9876", "-c", "aimall-default", "-t", topic);
                if (result.getExitCode() == 0) {
                    return;
                }
            } catch (Exception ignored) {
                // 退避重试
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("Topic 创建失败: " + topic);
    }

    /** 首次发送可能遭遇路由未同步（No route info），退避重试消除竞态 */
    private static void sendWithRetry(String topic, Envelope envelope) throws Exception {
        IllegalStateException last = null;
        for (int i = 0; i < 10; i++) {
            try {
                eventProducer.sendSync(topic, envelope);
                return;
            } catch (IllegalStateException e) {
                last = e;
                Thread.sleep(500);
            }
        }
        throw last;
    }

    private static void awaitUntil(String description, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        fail("等待超时: " + description);
    }

    private static Envelope envelope(String eventId, int version, String traceId, String eventType) {
        ObjectNode payload = MAPPER.createObjectNode().put("orderId", "50001");
        Envelope.Builder builder = Envelope.builder()
                .eventId(eventId)
                .eventType(eventType)
                .eventVersion(version)
                .occurredAt(Instant.parse("2026-08-01T06:00:00Z"))
                .producer("mall-order")
                .payload(payload);
        return traceId == null ? builder.build() : builder.traceId(traceId).build();
    }

    private static long countHandled(List<Envelope> handled, String eventId) {
        return handled.stream().filter(e -> eventId.equals(e.getEventId())).count();
    }

    private static long countDelivered(List<MessageExt> delivered, String key) {
        return delivered.stream().filter(m -> key.equals(m.getKeys())).count();
    }

    @Test
    @DisplayName("TC-003+TC-004：sendSync 送达，Tag=eventType/keys=eventId/七字段往返一致，MDC traceId 透传")
    void sendSync_deliversWithContractAndTraceId() throws Exception {
        String eventId = "evt-sync-" + UUID.randomUUID();
        sendWithRetry(TOPIC_MAIN, envelope(eventId, 1, "trace-it-sync-001", "IT_EVENT"));

        awaitUntil("happy handler 收到 sync 消息", () -> countHandled(happy.handled, eventId) > 0);
        MessageExt delivered = deliveredHappy.stream()
                .filter(m -> eventId.equals(m.getKeys())).findFirst().orElseThrow();

        // 消息契约：Tag=eventType、keys=eventId
        assertThat(delivered.getTags()).isEqualTo("IT_EVENT");
        assertThat(delivered.getKeys()).isEqualTo(eventId);

        int index = 0;
        for (int i = 0; i < happy.handled.size(); i++) {
            if (eventId.equals(happy.handled.get(i).getEventId())) {
                index = i;
                break;
            }
        }
        Envelope received = happy.handled.get(index);
        // Envelope 七字段往返一致
        assertThat(received.getEventType()).isEqualTo("IT_EVENT");
        assertThat(received.getEventVersion()).isEqualTo(1);
        assertThat(received.getOccurredAt()).isEqualTo(Instant.parse("2026-08-01T06:00:00Z"));
        assertThat(received.getProducer()).isEqualTo("mall-order");
        assertThat(received.getTraceId()).isEqualTo("trace-it-sync-001");
        assertThat(received.getPayload().get("orderId").asText()).isEqualTo("50001");
        // ②步 MDC：消费端 MDC.traceId == Envelope.traceId
        assertThat(happy.observedTraceIds.get(index)).isEqualTo("trace-it-sync-001");
    }

    @Test
    @DisplayName("TC-003：sendAsync 回调成功且消息送达")
    void sendAsync_deliversAndNotifiesCallback() throws Exception {
        String eventId = "evt-async-" + UUID.randomUUID();
        CountDownLatch callback = new CountDownLatch(1);

        eventProducer.sendAsync(TOPIC_MAIN, envelope(eventId, 1, "trace-it-async", "IT_EVENT"),
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult result) {
                        callback.countDown();
                    }

                    @Override
                    public void onException(Throwable e) {
                        callback.countDown();
                    }
                });

        assertThat(callback.await(30, TimeUnit.SECONDS)).isTrue();
        awaitUntil("happy handler 收到 async 消息", () -> countHandled(happy.handled, eventId) > 0);
    }

    @Test
    @DisplayName("TC-003：sendDelay delayLevel=1（1s）延迟消息送达")
    void sendDelay_deliversAfterDelayLevel() throws Exception {
        String eventId = "evt-delay-" + UUID.randomUUID();
        eventProducer.sendDelay(TOPIC_MAIN, envelope(eventId, 1, "trace-it-delay", "IT_EVENT"), 1);

        awaitUntil("happy handler 收到延迟消息", () -> countHandled(happy.handled, eventId) > 0);
    }

    @Test
    @DisplayName("TC-004：Envelope.traceId 缺失 → 生产者自动生成、消费端 MDC 可见且不阻断")
    void missingTraceId_generatedAndPropagated() throws Exception {
        String eventId = "evt-notrace-" + UUID.randomUUID();
        sendWithRetry(TOPIC_MAIN, envelope(eventId, 1, null, "IT_EVENT"));

        awaitUntil("happy handler 收到无 traceId 消息", () -> countHandled(happy.handled, eventId) > 0);
        int index = 0;
        for (int i = 0; i < happy.handled.size(); i++) {
            if (eventId.equals(happy.handled.get(i).getEventId())) {
                index = i;
                break;
            }
        }
        String generated = happy.handled.get(index).getTraceId();
        assertThat(generated).isNotBlank().hasSize(32);
        assertThat(happy.observedTraceIds.get(index)).isEqualTo(generated);
    }

    @Test
    @DisplayName("TC-005：eventVersion=2 → maxSupportedVersion=1 消费组 WARN+ACK 拒绝（handler 未执行），对照组正常收到")
    void version2_rejectedByStrictGroup() throws Exception {
        String eventId = "evt-v2-" + UUID.randomUUID();
        sendWithRetry(TOPIC_MAIN, envelope(eventId, 2, "trace-it-v2", "IT_EVENT"));

        // 对照组（maxSupportedVersion=2）收到 → 证明事件已送达 broker
        awaitUntil("v2 对照组收到消息", () -> countHandled(v2Accepting.handled, eventId) > 0);

        // 严格组未执行 handler 且该消息被 ACK（不重试：投递恰好 1 次）
        assertThat(countHandled(happy.handled, eventId)).isZero();
        awaitUntil("严格组投递计数稳定为 1", () -> countDelivered(deliveredHappy, eventId) >= 1);
        assertThat(countDelivered(deliveredHappy, eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-006：持续失败 → 删占位重试 3 次（初始+2）→ 超限进 DLQ 可查询，不静默")
    void persistentFailure_retriesThenDlq() throws Exception {
        String eventId = "evt-fail-" + UUID.randomUUID();
        sendWithRetry(TOPIC_FAIL, envelope(eventId, 1, "trace-it-fail", "FAIL_EVENT"));

        // DLQ 消息出现 → 说明重试次数耗尽（初始 + 2 次重试）
        awaitUntil("DLQ 查询入口查到失败消息", () ->
                dlqBodies.stream().anyMatch(body -> body.contains(eventId)));

        long attempts = failing.attempts.stream().filter(eventId::equals).count();
        assertThat(attempts).isEqualTo(3);
        // DLQ 消息体保留 Envelope 原文（可追溯，不静默丢弃）
        String dlqBody = dlqBodies.stream().filter(body -> body.contains(eventId)).findFirst().orElseThrow();
        assertThat(dlqBody).contains("\"eventType\":\"FAIL_EVENT\"");
    }

    @Test
    @DisplayName("AC-007 前置：同 eventId 重复投递 → 真实 SQL 幂等占位生效，handler 只执行一次")
    void duplicateDelivery_idempotentSkipped() throws Exception {
        String eventId = "evt-dup-" + UUID.randomUUID();
        sendWithRetry(TOPIC_MAIN, envelope(eventId, 1, "trace-it-dup", "IT_EVENT"));
        sendWithRetry(TOPIC_MAIN, envelope(eventId, 1, "trace-it-dup", "IT_EVENT"));

        awaitUntil("两条消息均已投递到消费组", () -> countDelivered(deliveredHappy, eventId) >= 2);

        assertThat(countHandled(happy.handled, eventId)).isEqualTo(1);
    }
}
