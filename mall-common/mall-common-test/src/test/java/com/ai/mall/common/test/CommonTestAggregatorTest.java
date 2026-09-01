package com.ai.mall.common.test;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 占位测试：保证 mall-common-test 参与 mvn test，并验证聚合器自身的测试环境可用
 * （JUnit5 + AssertJ 就绪，即服务引用本模块将获得的能力）。
 */
class CommonTestAggregatorTest {

    @Test
    void testEnvironmentShouldBeReady() {
        assertThat("mall-common-test").isNotBlank();
    }
}
