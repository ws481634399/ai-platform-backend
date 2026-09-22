package com.ai.mall.common.mq.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.mall.common.mq.consumer.ConsumedEventRepository;
import com.ai.mall.common.mq.consumer.IdempotentConsumer;
import com.ai.mall.common.mq.producer.IntegrationEventProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * TC-007 开关装配切片测试：rocketmq.enabled=false（或缺省）→ 全部 MQ Bean 不装配（无 NoOp 半开态）；
 * true → Producer/IntegrationEventProducer/IdempotentConsumer 装配齐全；幂等组件 fail-closed
 * ——无 JdbcTemplate（消费方数据库）时不装配 IdempotentConsumer。
 */
class RocketMQAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RocketMQAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    @DisplayName("缺省（未配置 rocketmq.enabled）→ 不装配任何 MQ Bean，服务以同步路径正常启动")
    void disabledByDefault_noMqBeans() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(DefaultMQProducer.class);
            assertThat(context).doesNotHaveBean(IntegrationEventProducer.class);
            assertThat(context).doesNotHaveBean(IdempotentConsumer.class);
            assertThat(context).doesNotHaveBean(RocketMQProperties.class);
        });
    }

    @Test
    @DisplayName("enabled=true + JdbcTemplate → Producer/发送器/幂等组件装配齐全，属性绑定生效")
    void enabled_allBeansWired() {
        runner.withPropertyValues(
                        "rocketmq.enabled=true",
                        "rocketmq.name-server=127.0.0.1:19876",
                        "rocketmq.producer-group=it-producer-group")
                .withBean(JdbcTemplate.class,
                        () -> new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:slice;MODE=MySQL")))
                .run(context -> {
                    assertThat(context).hasSingleBean(DefaultMQProducer.class);
                    assertThat(context).hasSingleBean(IntegrationEventProducer.class);
                    assertThat(context).hasSingleBean(IdempotentConsumer.class);
                    assertThat(context.getBean(IdempotentConsumer.class))
                            .isInstanceOf(ConsumedEventRepository.class);
                    RocketMQProperties properties = context.getBean(RocketMQProperties.class);
                    assertThat(properties.getNameServer()).isEqualTo("127.0.0.1:19876");
                    assertThat(properties.getProducerGroup()).isEqualTo("it-producer-group");
                    assertThat(properties.getMaxReconsumeTimes()).isEqualTo(16);
                });
    }

    @Test
    @DisplayName("enabled=true 但无 JdbcTemplate → fail-closed：幂等组件不装配（禁止无幂等消费），Producer 照常装配")
    void enabledWithoutJdbcTemplate_idempotentAbsent() {
        runner.withPropertyValues("rocketmq.enabled=true", "rocketmq.name-server=127.0.0.1:19876")
                .run(context -> {
                    assertThat(context).hasSingleBean(DefaultMQProducer.class);
                    assertThat(context).doesNotHaveBean(IdempotentConsumer.class);
                });
    }
}
