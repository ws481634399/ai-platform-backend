package com.ai.mall.system.interfaces.rest.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 功能开关管理端 DTO（CHG-0022）。 */
public final class FeatureDtos {

    private FeatureDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "configKey 不能为空") @Size(max = 100) String key,
            @NotBlank(message = "功能名称不能为空") @Size(max = 100) String name,
            String group,
            boolean enabled,
            boolean publicFlag,
            @Size(max = 500) String description) {
    }

    public record UpdateRequest(
            @NotBlank(message = "功能名称不能为空") @Size(max = 100) String name,
            String group,
            boolean enabled,
            boolean publicFlag,
            @Size(max = 500) String description,
            int version,
            @Size(max = 500) String changeReason) {
    }

    public record View(String key, String name, String group, boolean enabled, boolean publicFlag,
                       boolean builtIn, int version, String description) {
    }
}
