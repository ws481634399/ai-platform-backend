package com.ai.mall.search;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 上下文冒烟测试：验证 Spring 上下文可装配启动
 * （H2 test profile 承载数据源，Flyway 空迁移集执行，Nacos 关闭）
 */
@SpringBootTest
@ActiveProfiles("test")
class MallSearchApplicationSmokeTest {

    @Test
    void contextLoads() {
    }
}
