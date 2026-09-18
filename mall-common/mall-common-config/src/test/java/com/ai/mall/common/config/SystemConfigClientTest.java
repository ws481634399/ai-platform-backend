package com.ai.mall.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * SystemConfigClient 三级读链路单测（CHG-0022 DU-BE-508）：
 * 本地 TTL 缓存 → Redis 共享缓存（含负标记）→ HTTP；全链路故障安全降级 empty。
 * HTTP 桩用 JDK 内置 HttpServer，零第三方依赖。
 */
class SystemConfigClientTest {

    private HttpServer httpServer;
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
    private String httpBaseUri;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpBaseUri = "http://127.0.0.1:" + httpServer.getAddress().getPort();
        httpServer.start();
    }

    @AfterEach
    void tearDown() {
        httpServer.stop(0);
    }

    private SystemConfigClient client() {
        return client(60L);
    }

    private SystemConfigClient client(long localTtlSeconds) {
        SystemConfigProperties props = new SystemConfigProperties();
        props.setSystemBaseUri(httpBaseUri == null ? "http://127.0.0.1:1" : httpBaseUri);
        props.setLocalTtlSeconds(localTtlSeconds);
        props.setConnectTimeoutMs(300L);
        props.setReadTimeoutMs(1000L);
        return new SystemConfigClient(props, redisTemplate, objectMapper, "test");
    }

    @Test
    @DisplayName("Redis 命中：返回快照；二次读走本地缓存，不再访问 Redis")
    void redisHitThenLocalCache() {
        when(valueOps.get("aimall:test:system:feature:search.enabled"))
                .thenReturn("{\"key\":\"search.enabled\",\"enabled\":true,\"version\":3}");
        SystemConfigClient client = client();

        Optional<FeatureSnapshot> first = client.getFeature("search.enabled");
        Optional<FeatureSnapshot> second = client.getFeature("search.enabled");

        assertThat(first).isPresent().get().extracting(FeatureSnapshot::enabled).isEqualTo(true);
        assertThat(second).isPresent().get().extracting(FeatureSnapshot::version).isEqualTo(3L);
        verify(valueOps, times(1)).get(anyString());
    }

    @Test
    @DisplayName("Redis 负向标记：结论 empty 且本地缓存负结论，不穿透 HTTP")
    void redisNegativeMarkerCached() {
        stubHttp("/api/internal/config/features",
                "{\"success\":true,\"data\":{\"values\":{},\"missingKeys\":[\"missing.flag\"]}}");
        when(valueOps.get("aimall:test:system:feature:missing.flag"))
                .thenReturn("{\"missing\":true}");
        SystemConfigClient client = client();

        assertThat(client.getFeature("missing.flag")).isEmpty();
        assertThat(client.getFeature("missing.flag")).isEmpty();
        verify(valueOps, times(1)).get(anyString());
    }

    @Test
    @DisplayName("Redis 无结论 → HTTP 回源：解析契约并携带内部令牌头")
    void redisMissFallbackToHttp() {
        when(valueOps.get(anyString())).thenReturn(null);
        stubHttp("/api/internal/config/features",
                "{\"success\":true,\"data\":{\"values\":"
                        + "{\"search.enabled\":{\"key\":\"search.enabled\",\"enabled\":false,\"version\":7}},"
                        + "\"missingKeys\":[]}}");
        SystemConfigClient client = client();

        FeatureSnapshot snapshot = client.getFeature("search.enabled").orElseThrow();
        assertThat(snapshot.enabled()).isFalse();
        assertThat(snapshot.version()).isEqualTo(7L);
        assertThat(lastAuthHeader.get()).isEqualTo("dev-internal-secret");
    }

    @Test
    @DisplayName("HTTP 参数契约兼容 configValue/parameterType 全名")
    void httpParameterFullFieldNames() {
        when(valueOps.get(anyString())).thenReturn(null);
        stubHttp("/api/internal/config/parameters",
                "{\"success\":true,\"data\":{\"values\":"
                        + "{\"p.int\":{\"key\":\"p.int\",\"configValue\":\"42\",\"parameterType\":\"INTEGER\","
                        + "\"minValue\":\"1\",\"maxValue\":\"100\",\"version\":2}},"
                        + "\"missingKeys\":[]}}");
        SystemConfigClient client = client();

        ParameterSnapshot snapshot = client.getParameter("p.int").orElseThrow();
        assertThat(snapshot.value()).isEqualTo("42");
        assertThat(snapshot.type()).isEqualTo("INTEGER");
    }

    @Test
    @DisplayName("Redis 异常且 HTTP 缺键（missingKeys）→ empty，不抛异常")
    void redisFailureAndHttpMissing() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        stubHttp("/api/internal/config/features",
                "{\"success\":true,\"data\":{\"values\":{},\"missingKeys\":[\"ghost.key\"]}}");
        SystemConfigClient client = client();

        assertThat(client.getFeature("ghost.key")).isEmpty();
    }

    @Test
    @DisplayName("全链路不可用 → empty（故障安全）")
    void allLayersDown() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        SystemConfigProperties props = new SystemConfigProperties();
        props.setSystemBaseUri("http://127.0.0.1:1");
        props.setConnectTimeoutMs(200L);
        props.setReadTimeoutMs(200L);
        SystemConfigClient client = new SystemConfigClient(props, redisTemplate, objectMapper, "test");

        assertThat(client.getFeature("search.enabled")).isEmpty();
        assertThat(client.getParameter("p.any")).isEmpty();
    }

    @Test
    @DisplayName("本地缓存 TTL 到期后重新回源（TTL=1s）")
    void localCacheExpires() throws InterruptedException {
        when(valueOps.get("aimall:test:system:feature:search.enabled"))
                .thenReturn("{\"key\":\"search.enabled\",\"enabled\":true,\"version\":1}")
                .thenReturn("{\"key\":\"search.enabled\",\"enabled\":false,\"version\":2}");
        SystemConfigClient client = client(1L);

        assertThat(client.getFeature("search.enabled").orElseThrow().enabled()).isTrue();
        Thread.sleep(1100L);
        assertThat(client.getFeature("search.enabled").orElseThrow().enabled()).isFalse();
        verify(valueOps, times(2)).get(anyString());
    }

    private void stubHttp(String path, String responseBody) {
        httpServer.createContext(path, exchange -> {
            lastAuthHeader.set(exchange.getRequestHeaders().getFirst(SystemConfigClient.INTERNAL_TOKEN_HEADER));
            writeJson(exchange, responseBody);
        });
    }

    private static void writeJson(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
