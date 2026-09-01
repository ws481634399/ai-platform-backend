package com.ai.mall.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文冒烟测试：验证 WebFlux 上下文可装配启动
 * （无数据源，Nacos 默认关闭；WebFlux 栈隔离，不依赖 mall-common-web）
 */
@SpringBootTest
class MallGatewayApplicationSmokeTest {

    @Test
    void contextLoads() {
    }
}
