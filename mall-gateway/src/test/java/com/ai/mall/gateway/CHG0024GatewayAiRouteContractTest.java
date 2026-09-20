package com.ai.mall.gateway;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * CHG-0024 DU-BE-001 路由契约：/api/ai/** 全量透传 ai-service（stripPrefix=false，路径完整收口）。
 */
class CHG0024GatewayAiRouteContractTest {

    @Test
    void aiServiceRouteIsConfigured() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("Path=/api/ai/**"));
        assertTrue(yaml.contains("${MALL_GATEWAY_AI_URI:http://localhost:8120}"));
    }
}
