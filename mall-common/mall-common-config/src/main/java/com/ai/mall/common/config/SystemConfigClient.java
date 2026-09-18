package com.ai.mall.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 系统配置统一读客户端（CHG-0022）：本地 TTL 缓存 → Redis 共享缓存 → mall-system HTTP。
 *
 * <p>冻结约束：
 * <ul>
 *   <li>本地缓存 ConcurrentHashMap + expireAt（不引 Caffeine），TTL 由 mall.config.local-ttl-seconds 控制；</li>
 *   <li>Redis 键 {@code aimall:{env}:system:feature|parameter:{key}}，负向标记 {@code {"missing":true}}
 *       由 mall-system 侧写入（TTL 60s），客户端识别后不再穿透；</li>
 *   <li>HTTP 成功后客户端不回写 Redis（由 system 读路径回填）；</li>
 *   <li>任一层故障都不外抛：本层返回 empty，由 {@link FeatureGate}/{@link SystemParameterProvider}
 *       执行调用方传入的安全默认值（WARN 含 key）。</li>
 * </ul>
 */
public class SystemConfigClient {

    /** 内部令牌头（与 mall-common-security InternalIdentityFilter 常量同值，避免反向依赖）。 */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private static final Logger log = LoggerFactory.getLogger(SystemConfigClient.class);
    private static final String MISSING_MARKER = "{\"missing\":true}";

    private final SystemConfigProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String featureKeyPrefix;
    private final String parameterKeyPrefix;
    private final long localTtlMillis;

    /** 本地缓存：value 为 Optional（empty 表示负缓存），不主动清理线程。 */
    private final Map<String, LocalEntry> localCache = new ConcurrentHashMap<>();

    public SystemConfigClient(SystemConfigProperties properties, StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper, String env) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.localTtlMillis = Math.max(0L, properties.getLocalTtlSeconds()) * 1000L;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getSystemBaseUri())
                .requestFactory(buildFactory(properties))
                .defaultHeader(INTERNAL_TOKEN_HEADER, properties.getInternalToken())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        String prefix = "aimall:" + (env == null || env.isBlank() ? "dev" : env) + ":system:";
        this.featureKeyPrefix = prefix + "feature:";
        this.parameterKeyPrefix = prefix + "parameter:";
    }

    /** 读取功能开关快照；任何故障/缺键均以 empty 表达。 */
    public Optional<FeatureSnapshot> getFeature(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String localKey = "F:" + key;
        Optional<LocalEntry> local = localEntry(localKey);
        if (local.isPresent()) {
            return local.get().featureOp();
        }
        Optional<FeatureSnapshot> fromRedis = readFeatureFromRedis(key);
        if (fromRedis != null) {
            putLocal(localKey, LocalEntry.feature(fromRedis.orElse(null)));
            return fromRedis;
        }
        Optional<FeatureSnapshot> fromHttp = fetchFeatureFromHttp(key);
        putLocal(localKey, LocalEntry.feature(fromHttp.orElse(null)));
        return fromHttp;
    }

    /** 读取系统参数快照；任何故障/缺键均以 empty 表达。 */
    public Optional<ParameterSnapshot> getParameter(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String localKey = "P:" + key;
        Optional<LocalEntry> local = localEntry(localKey);
        if (local.isPresent()) {
            return local.get().parameterOp();
        }
        Optional<ParameterSnapshot> fromRedis = readParameterFromRedis(key);
        if (fromRedis != null) {
            putLocal(localKey, LocalEntry.parameter(fromRedis.orElse(null)));
            return fromRedis;
        }
        Optional<ParameterSnapshot> fromHttp = fetchParameterFromHttp(key);
        putLocal(localKey, LocalEntry.parameter(fromHttp.orElse(null)));
        return fromHttp;
    }

    /** 测试/极端运维用：清空本地缓存（正常情况下仅靠 TTL 过期）。 */
    public void clearLocalCache() {
        localCache.clear();
    }

    private Optional<LocalEntry> localEntry(String localKey) {
        LocalEntry entry = localCache.get(localKey);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expireAt() < System.currentTimeMillis()) {
            localCache.remove(localKey, entry);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    private void putLocal(String localKey, LocalEntry entry) {
        localCache.put(localKey, entry.withExpireAt(System.currentTimeMillis() + localTtlMillis));
    }

    /**
     * @return Redis 层有结论（含负标记）时返回 Optional；Redis 不可用或无键返回 null（需继续向下）。
     */
    private Optional<FeatureSnapshot> readFeatureFromRedis(String key) {
        try {
            String raw = redisTemplate.opsForValue().get(featureKeyPrefix + key);
            if (raw == null) {
                return null;
            }
            if (MISSING_MARKER.equals(raw)) {
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(raw);
            if (node.path("missing").asBoolean(false)) {
                return Optional.empty();
            }
            return Optional.of(new FeatureSnapshot(
                    node.path("key").asText(key),
                    node.path("enabled").asBoolean(false),
                    node.path("version").asLong(0)));
        } catch (Exception ex) {
            log.warn("读取功能开关 Redis 缓存失败，降级 HTTP key={}", key, ex);
            return null;
        }
    }

    private Optional<ParameterSnapshot> readParameterFromRedis(String key) {
        try {
            String raw = redisTemplate.opsForValue().get(parameterKeyPrefix + key);
            if (raw == null) {
                return null;
            }
            if (MISSING_MARKER.equals(raw)) {
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(raw);
            if (node.path("missing").asBoolean(false)) {
                return Optional.empty();
            }
            return Optional.of(new ParameterSnapshot(
                    node.path("key").asText(key),
                    node.path("value").asText(""),
                    node.path("type").asText("STRING"),
                    node.path("version").asLong(0)));
        } catch (Exception ex) {
            log.warn("读取系统参数 Redis 缓存失败，降级 HTTP key={}", key, ex);
            return null;
        }
    }

    private Optional<FeatureSnapshot> fetchFeatureFromHttp(String key) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/internal/config/features")
                            .queryParam("keys", key).build())
                    .retrieve()
                    .toEntity(String.class);
            JsonNode data = unwrapData(response.getBody());
            JsonNode value = data == null ? null : data.path("values").path(key);
            if (value == null || value.isMissingNode() || value.isNull()) {
                return Optional.empty();
            }
            return Optional.of(new FeatureSnapshot(
                    value.path("key").asText(key),
                    value.path("enabled").asBoolean(false),
                    value.path("version").asLong(0)));
        } catch (Exception ex) {
            log.warn("功能开关全链路读取失败，调用方将使用默认值 key={}", key, ex);
            return Optional.empty();
        }
    }

    private Optional<ParameterSnapshot> fetchParameterFromHttp(String key) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/internal/config/parameters")
                            .queryParam("keys", key).build())
                    .retrieve()
                    .toEntity(String.class);
            JsonNode data = unwrapData(response.getBody());
            JsonNode value = data == null ? null : data.path("values").path(key);
            if (value == null || value.isMissingNode() || value.isNull()) {
                return Optional.empty();
            }
            return Optional.of(new ParameterSnapshot(
                    value.path("key").asText(key),
                    value.path("configValue").asText(value.path("value").asText("")),
                    value.path("parameterType").asText(value.path("type").asText("STRING")),
                    value.path("version").asLong(0)));
        } catch (Exception ex) {
            log.warn("系统参数全链路读取失败，调用方将使用默认值 key={}", key, ex);
            return Optional.empty();
        }
    }

    private JsonNode unwrapData(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode root = objectMapper.readTree(body);
        if (!root.path("success").asBoolean(false)) {
            log.warn("配置服务返回业务失败 body={}", body);
            return null;
        }
        return root.path("data");
    }

    private static org.springframework.http.client.ClientHttpRequestFactory buildFactory(
            SystemConfigProperties properties) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return factory;
    }

    /** 本地缓存条目：feature/parameter 二选一，二者皆 null 即负缓存结论。 */
    private record LocalEntry(FeatureSnapshot feature, ParameterSnapshot parameter, long expireAt) {

        static LocalEntry feature(FeatureSnapshot snapshot) {
            return new LocalEntry(snapshot, null, 0L);
        }

        static LocalEntry parameter(ParameterSnapshot snapshot) {
            return new LocalEntry(null, snapshot, 0L);
        }

        Optional<FeatureSnapshot> featureOp() {
            return feature == null ? Optional.empty() : Optional.of(feature);
        }

        Optional<ParameterSnapshot> parameterOp() {
            return parameter == null ? Optional.empty() : Optional.of(parameter);
        }

        LocalEntry withExpireAt(long expireAt) {
            return new LocalEntry(feature, parameter, expireAt);
        }
    }
}
