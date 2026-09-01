package com.ai.mall.common.web.testsupport;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 测试专用配置类：@WebMvcTest 切片要求的 @SpringBootConfiguration 入口。
 * 仅存在于 test classpath，不参与生产构建。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestApplication {
}
