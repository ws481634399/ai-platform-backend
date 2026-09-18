package com.ai.mall.system.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.system.application.config.ConfigHistoryAppService;
import com.ai.mall.system.domain.config.ConfigHistory;
import com.ai.mall.system.domain.config.ConfigTargetType;
import com.ai.mall.system.interfaces.rest.admin.dto.HistoryView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置变更历史查询端（CHG-0022，只读）。
 * 权限码：system:config-history:list（identity V10 种子）。
 */
@RestController
@RequestMapping("/api/admin/config-history")
public class ConfigHistoryAdminController {

    private final ConfigHistoryAppService appService;

    public ConfigHistoryAdminController(ConfigHistoryAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:config-history:list')")
    public UnifyResult<Map<String, Object>> page(
            @RequestParam(name = "configType", required = false) String configType,
            @RequestParam(name = "key", required = false) String key,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        ConfigTargetType type = parseType(configType);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        List<HistoryView> items = appService.page(type, key, safePage, safeSize)
                .stream().map(ConfigHistoryAdminController::toView).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", appService.count(type, key));
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("items", items);
        return UnifyResult.ok(data);
    }

    private static ConfigTargetType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ConfigTargetType.valueOf(raw.trim().toUpperCase());
    }

    private static HistoryView toView(ConfigHistory h) {
        return new HistoryView(h.configType().name(), h.configKey(), h.oldValue(), h.newValue(),
                h.changeKind().name(), h.changedBy(), h.changeReason(), h.traceId(),
                h.createdAt() == null ? 0L : h.createdAt().toEpochMilli());
    }
}
