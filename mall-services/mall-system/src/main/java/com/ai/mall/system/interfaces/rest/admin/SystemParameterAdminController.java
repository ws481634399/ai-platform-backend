package com.ai.mall.system.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.system.application.config.SystemParameterAppService;
import com.ai.mall.system.domain.config.SystemParameter;
import com.ai.mall.system.interfaces.rest.admin.dto.ParameterDtos.CreateRequest;
import com.ai.mall.system.interfaces.rest.admin.dto.ParameterDtos.UpdateRequest;
import com.ai.mall.system.interfaces.rest.admin.dto.ParameterDtos.View;
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
 * 系统参数管理端（CHG-0022）。
 * 权限码：system:parameter:list / system:parameter:update（identity V10 种子）。
 */
@RestController
@RequestMapping("/api/admin/system-parameters")
public class SystemParameterAdminController {

    private final SystemParameterAppService appService;

    public SystemParameterAdminController(SystemParameterAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:parameter:list')")
    public UnifyResult<Map<String, Object>> page(
            @RequestParam(name = "group", required = false) String group,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        List<View> items = appService.page(group, type, safePage, safeSize)
                .stream().map(SystemParameterAdminController::toView).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", appService.count(group, type));
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("items", items);
        return UnifyResult.ok(data);
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasAuthority('system:parameter:list')")
    public UnifyResult<View> getOne(@PathVariable("key") String key) {
        return UnifyResult.ok(toView(appService.getByKey(key)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:parameter:update')")
    public UnifyResult<View> create(@Valid @RequestBody CreateRequest request) {
        SystemParameter created = appService.create(new SystemParameterAppService.CreateParameterCommand(
                request.key(), request.name(), request.group(), request.type(), request.value(),
                request.defaultValue(), request.minValue(), request.maxValue(), request.effectType(),
                request.publicFlag(), request.description()));
        return UnifyResult.ok(toView(created));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('system:parameter:update')")
    public UnifyResult<View> update(@PathVariable("key") String key, @Valid @RequestBody UpdateRequest request) {
        SystemParameter updated = appService.update(key, new SystemParameterAppService.UpdateParameterCommand(
                request.name(), request.group(), request.value(), request.defaultValue(),
                request.minValue(), request.maxValue(), request.publicFlag(), request.description(),
                request.version(), request.changeReason()));
        return UnifyResult.ok(toView(updated));
    }

    @DeleteMapping("/{key}")
    @PreAuthorize("hasAuthority('system:parameter:update')")
    public UnifyResult<Void> delete(@PathVariable("key") String key,
                                    @RequestParam(name = "reason", required = false) String reason) {
        appService.delete(key, reason);
        return UnifyResult.ok();
    }

    static View toView(SystemParameter p) {
        return new View(p.getKey(), p.getName(), p.getGroup(), p.getType().name(), p.getValue(),
                p.getDefaultValue(), p.getMinValue(), p.getMaxValue(), p.getEffectType().name(),
                p.isPublicFlag(), p.isBuiltIn(), p.getVersion(), p.getDescription());
    }
}
