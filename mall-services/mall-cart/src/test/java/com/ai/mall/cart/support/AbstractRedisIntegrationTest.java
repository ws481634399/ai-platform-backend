package com.ai.mall.cart.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 真实 Redis Testcontainers 基类（CHG-0018 DU-BE-801）。
 * 每 JVM 启动一个 Redis 7 容器，供 Lua 脚本集成测试与全链路 MockMvc 测试复用；
 * 密码留空，与测试 spring.data.redis 配置对齐。
 */
@Testcontainers
public abstract class AbstractRedisIntegrationTest {

    static {
        // 双保险：环境不具备 Ryuk 镜像时避免阻塞容器启动（surefire 已注入同名环境变量）
        if (System.getProperty("testcontainers.ryuk.disabled") == null) {
            System.setProperty("testcontainers.ryuk.disabled", "true");
        }
    }

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4.11-alpine").asCompatibleSubstituteFor("redis"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--save", "", "--appendonly", "no");

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }
}
