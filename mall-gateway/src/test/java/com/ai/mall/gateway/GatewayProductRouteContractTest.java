package com.ai.mall.gateway;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GatewayProductRouteContractTest {

    @Test
    void productMallAndInternalRoutesAreConfigured() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("Path=/api/mall/products/**"));
        assertTrue(yaml.contains("Path=/api/internal/products/**"));
    }
}
