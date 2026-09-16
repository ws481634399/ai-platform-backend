package com.ai.mall.cart;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 上下文冒烟测试：验证 Spring 上下文可装配启动
 * （test profile 排除数据源自动装配，Redis 懒连接，Nacos 关闭）
 */
@SpringBootTest
@ActiveProfiles("test")
class MallCartApplicationSmokeTest {

    @Test
    void contextLoads() {
    }
}
