package com.ai.mall.system.interfaces.rest.internal;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.system.infrastructure.cache.ConfigCacheService;
import com.ai.mall.system.infrastructure.cache.FeatureCacheView;
import com.ai.mall.system.infrastructure.cache.ParameterCacheView;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置内部端点（CHG-0022）：仅供各业务服务经 X-Internal-Token 调用（安全链 /api/internal/**）。
 *
 * <p>契约（story-design STORY-006-02-01-01 §2 冻结）：
 * <ul>
 *   <li>必须显式传 keys（逗号分隔），空 → B0601 400；单次最多 100 个；</li>
 *   <li>响应 {@code {values:{key:snapshot},missingKeys:[...]}}；缺键不报错。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/internal/config")
public class InternalConfigController {

    private static final int MAX_KEYS = 100;

    private final ConfigCacheService cacheService;

    public InternalConfigController(ConfigCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @GetMapping("/features")
    public UnifyResult<Map<String, Object>> features(@RequestParam("keys") String keysRaw) {
        List<String> keys = parseKeys(keysRaw);
        Map<String, FeatureCacheView> values = cacheService.featureSnapshots(keys);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("values", values);
        data.put("missingKeys", keys.stream().filter(k -> !values.containsKey(k)).toList());
        return UnifyResult.ok(data);
    }

    @GetMapping("/parameters")
    public UnifyResult<Map<String, Object>> parameters(@RequestParam("keys") String keysRaw) {
        List<String> keys = parseKeys(keysRaw);
        Map<String, ParameterCacheView> values = cacheService.parameterSnapshots(keys);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("values", values);
        data.put("missingKeys", keys.stream().filter(k -> !values.containsKey(k)).toList());
        return UnifyResult.ok(data);
    }

    private static List<String> parseKeys(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("keys 不能为空");
        }
        List<String> keys = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("keys 不能为空");
        }
        if (keys.size() > MAX_KEYS) {
            throw new IllegalArgumentException("单次查询 keys 不能超过 " + MAX_KEYS + " 个");
        }
        return keys;
    }
}
