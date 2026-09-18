package com.ai.mall.system.infrastructure.cache;

import com.ai.mall.system.domain.config.FeatureConfig;
import com.ai.mall.system.domain.config.FeatureConfigRepository;
import com.ai.mall.system.domain.config.SystemParameter;
import com.ai.mall.system.domain.config.SystemParameterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 系统配置共享缓存（CHG-0022）：mall-system 侧唯一 Redis 读写者。
 *
 * <ul>
 *   <li>键前缀 {@code aimall:{env}:system:}；单键 TTL 600s，负向空标记 TTL 60s，
 *       聚合键 public-features TTL 600s；</li>
 *   <li>读未命中回源 DB 并回填；Redis 故障降级直读 DB（不外抛）；</li>
 *   <li>写入路径只删不写：AFTER_COMMIT 删单键 + 恒删聚合键。</li>
 * </ul>
 */
@Component
public class ConfigCacheService {

    /** Redis 单键 TTL（秒）。 */
    public static final long VALUE_TTL_SECONDS = 600L;
    /** 负向空标记 TTL（秒，防穿透）。 */
    public static final long MISSING_TTL_SECONDS = 60L;

    private static final Logger log = LoggerFactory.getLogger(ConfigCacheService.class);
    private static final String MISSING_MARKER = "{\"missing\":true}";

    private final FeatureConfigRepository featureRepository;
    private final SystemParameterRepository parameterRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String featurePrefix;
    private final String parameterPrefix;
    private final String publicFeaturesKey;

    public ConfigCacheService(FeatureConfigRepository featureRepository,
                              SystemParameterRepository parameterRepository,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              Environment environment) {
        this.featureRepository = featureRepository;
        this.parameterRepository = parameterRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        // 与 mall-common-config SystemConfigClient 的键前缀约定一致：显式取激活 profile，
        // 不依赖 @Value("${spring.profiles.active}")（该占位在部分装配路径不可解析）。
        String[] profiles = environment.getActiveProfiles();
        String env = profiles.length == 0 ? "dev" : profiles[0];
        String prefix = "aimall:" + env + ":system:";
        this.featurePrefix = prefix + "feature:";
        this.parameterPrefix = prefix + "parameter:";
        this.publicFeaturesKey = prefix + "public-features";
    }

    /** 批量取开关（keys 去重保序）；缺失键不出现在返回 Map 中。 */
    public Map<String, FeatureCacheView> featureSnapshots(List<String> keys) {
        Map<String, FeatureCacheView> result = new LinkedHashMap<>();
        for (String key : distinct(keys)) {
            Slot<FeatureCacheView> slot = readFeatureSlot(key);
            if (slot.hasRedisConclusion()) {
                if (!slot.isMissing()) {
                    result.put(key, slot.value());
                }
                continue;
            }
            FeatureConfig config = featureRepository.findByKey(key).orElse(null);
            if (config == null) {
                writeRedis(featurePrefix + key, MISSING_MARKER, MISSING_TTL_SECONDS);
            } else {
                FeatureCacheView view = new FeatureCacheView(
                        config.getKey(), config.isEnabled(), config.getVersion());
                writeRedis(featurePrefix + key, toJson(Map.of(
                        "key", view.key(),
                        "enabled", view.enabled(),
                        "version", view.version())), VALUE_TTL_SECONDS);
                result.put(key, view);
            }
        }
        return result;
    }

    /** 批量取参数；缺失键不出现在返回 Map 中。 */
    public Map<String, ParameterCacheView> parameterSnapshots(List<String> keys) {
        Map<String, ParameterCacheView> result = new LinkedHashMap<>();
        for (String key : distinct(keys)) {
            Slot<ParameterCacheView> slot = readParameterSlot(key);
            if (slot.hasRedisConclusion()) {
                if (!slot.isMissing()) {
                    result.put(key, slot.value());
                }
                continue;
            }
            SystemParameter parameter = parameterRepository.findByKey(key).orElse(null);
            if (parameter == null) {
                writeRedis(parameterPrefix + key, MISSING_MARKER, MISSING_TTL_SECONDS);
            } else {
                ParameterCacheView view = new ParameterCacheView(parameter.getKey(), parameter.getValue(),
                        parameter.getType().name(), parameter.getMinValue(), parameter.getMaxValue(),
                        parameter.getVersion());
                // Redis 共享契约用 value/type 短名（requirement-design §2.3）
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("key", view.key());
                payload.put("value", view.configValue());
                payload.put("type", view.parameterType());
                payload.put("version", view.version());
                writeRedis(parameterPrefix + key, toJson(payload), VALUE_TTL_SECONDS);
                result.put(key, view);
            }
        }
        return result;
    }

    /** 公开开关聚合：仅 publicFlag=1（含禁用项）。 */
    public List<PublicFeatureView> publicFeatures() {
        String raw = readRaw(publicFeaturesKey);
        if (raw != null) {
            try {
                return objectMapper.readValue(raw,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, PublicFeatureView.class));
            } catch (Exception ex) {
                log.warn("公开开关聚合缓存反序列化失败，回源重建", ex);
            }
        }
        List<PublicFeatureView> features = featureRepository.findAllPublic().stream()
                .map(config -> new PublicFeatureView(config.getKey(), config.isEnabled()))
                .toList();
        writeRedis(publicFeaturesKey, toJson(features), VALUE_TTL_SECONDS);
        return features;
    }

    /** 写后失效：删单键，且恒删公开聚合键（任何开关变更都可能影响公开列表）。 */
    public void evict(com.ai.mall.system.domain.config.ConfigTargetType type, String key) {
        try {
            String single = type == com.ai.mall.system.domain.config.ConfigTargetType.FEATURE
                    ? featurePrefix + key : parameterPrefix + key;
            redisTemplate.delete(List.of(single, publicFeaturesKey));
        } catch (Exception ex) {
            // 删键失败仅告警：TTL 600s 兜底收敛，不阻断写事务（测试/无 Redis 环境同路径）
            log.warn("配置缓存失效失败 type={} key={}", type, key, ex);
        }
    }

    private Slot<FeatureCacheView> readFeatureSlot(String key) {
        String raw = readRaw(featurePrefix + key);
        if (raw == null) {
            return Slot.ofNone();
        }
        if (MISSING_MARKER.equals(raw)) {
            return Slot.ofMissing();
        }
        try {
            var node = objectMapper.readTree(raw);
            if (node.path("missing").asBoolean(false)) {
                return Slot.ofMissing();
            }
            return Slot.ofFound(new FeatureCacheView(node.path("key").asText(key),
                    node.path("enabled").asBoolean(false),
                    node.path("version").asLong(0)));
        } catch (Exception ex) {
            log.warn("功能开关缓存反序列化失败，回源 key={}", key, ex);
            return Slot.ofNone();
        }
    }

    private Slot<ParameterCacheView> readParameterSlot(String key) {
        String raw = readRaw(parameterPrefix + key);
        if (raw == null) {
            return Slot.ofNone();
        }
        if (MISSING_MARKER.equals(raw)) {
            return Slot.ofMissing();
        }
        try {
            var node = objectMapper.readTree(raw);
            if (node.path("missing").asBoolean(false)) {
                return Slot.ofMissing();
            }
            return Slot.ofFound(new ParameterCacheView(node.path("key").asText(key),
                    node.path("value").asText(""), node.path("type").asText("STRING"),
                    null, null, node.path("version").asLong(0)));
        } catch (Exception ex) {
            log.warn("参数缓存反序列化失败，回源 key={}", key, ex);
            return Slot.ofNone();
        }
    }

    private String readRaw(String redisKey) {
        try {
            return redisTemplate.opsForValue().get(redisKey);
        } catch (Exception ex) {
            log.warn("读取配置 Redis 缓存失败，降级回源 redisKey={}", redisKey, ex);
            return null;
        }
    }

    private void writeRedis(String redisKey, String json, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(redisKey, json, Duration.ofSeconds(ttlSeconds));
        } catch (Exception ex) {
            log.warn("写入配置 Redis 缓存失败（不影响主链路） redisKey={}", redisKey, ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("配置缓存序列化失败", ex);
        }
    }

    private static List<String> distinct(List<String> keys) {
        return keys.stream().filter(k -> k != null && !k.isBlank()).distinct().toList();
    }

    /** Redis 读取结论槽位：无结论（无键/故障）→ 回源；isMissing → 缺键；found → 命中值。 */
    private record Slot<T>(T value, boolean isMissing, boolean hasRedisConclusion) {

        static <T> Slot<T> ofNone() {
            return new Slot<>(null, false, false);
        }

        static <T> Slot<T> ofMissing() {
            return new Slot<>(null, true, true);
        }

        static <T> Slot<T> ofFound(T value) {
            return new Slot<>(value, false, true);
        }
    }
}
