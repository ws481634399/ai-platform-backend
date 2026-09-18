package com.ai.mall.system.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.system.application.config.FeatureConfigAppService;
import com.ai.mall.system.domain.config.FeatureConfig;
import com.ai.mall.system.interfaces.rest.admin.dto.FeatureDtos.CreateRequest;
import com.ai.mall.system.interfaces.rest.admin.dto.FeatureDtos.UpdateRequest;
import com.ai.mall.system.interfaces.rest.admin.dto.FeatureDtos.View;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 功能开关管理端（CHG-0022）。
 * 权限码：system:feature:list / system:feature:update（identity V10 种子）。
 */
@RestController
@RequestMapping("/api/admin/feature-configs")
public class FeatureConfigAdminController {

    private final FeatureConfigAppService appService;

    public FeatureConfigAdminController(FeatureConfigAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:feature:list')")
    public UnifyResult<Map<String, Object>> page(
            @RequestParam(name = "group", required = false) String group,
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        List<View> items = appService.page(group, enabled, safePage, safeSize)
                .stream().map(FeatureConfigAdminController::toView).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", appService.count(group, enabled));
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("items", items);
        return UnifyResult.ok(data);
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasAuthority('system:feature:list')")
    public UnifyResult<View> getOne(@PathVariable("key") String key) {
        return UnifyResult.ok(toView(appService.getByKey(key)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:feature:update')")
    public UnifyResult<View> create(@Valid @RequestBody CreateRequest request) {
        FeatureConfig created = appService.create(new FeatureConfigAppService.CreateFeatureCommand(
                request.key(), request.name(), request.group(), request.enabled(),
                request.publicFlag(), request.description()));
        return UnifyResult.ok(toView(created));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('system:feature:update')")
    public UnifyResult<View> update(@PathVariable("key") String key, @Valid @RequestBody UpdateRequest request) {
        FeatureConfig updated = appService.update(key, new FeatureConfigAppService.UpdateFeatureCommand(
                request.name(), request.group(), request.enabled(), request.publicFlag(),
                request.description(), request.version(), request.changeReason()));
        return UnifyResult.ok(toView(updated));
    }

    @DeleteMapping("/{key}")
    @PreAuthorize("hasAuthority('system:feature:update')")
    public UnifyResult<Void> delete(@PathVariable("key") String key,
                                    @RequestParam(name = "reason", required = false) String reason) {
        appService.delete(key, reason);
        return UnifyResult.ok();
    }

    static View toView(FeatureConfig c) {
        return new View(c.getKey(), c.getName(), c.getGroup(), c.isEnabled(), c.isPublicFlag(),
                c.isBuiltIn(), c.getVersion(), c.getDescription());
    }
}
