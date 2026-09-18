package com.ai.mall.system.interfaces.rest.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 系统参数管理端 DTO（CHG-0022）。 */
public final class ParameterDtos {

    private ParameterDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "configKey 不能为空") @Size(max = 100) String key,
            @NotBlank(message = "参数名称不能为空") @Size(max = 100) String name,
            String group,
            @NotBlank(message = "parameterType 不能为空") String type,
            @NotBlank(message = "参数值不能为空") @Size(max = 1000) String value,
            @Size(max = 1000) String defaultValue,
            @Size(max = 64) String minValue,
            @Size(max = 64) String maxValue,
            String effectType,
            boolean publicFlag,
            @Size(max = 500) String description) {
    }

    public record UpdateRequest(
            @NotBlank(message = "参数名称不能为空") @Size(max = 100) String name,
            String group,
            @NotBlank(message = "参数值不能为空") @Size(max = 1000) String value,
            @Size(max = 1000) String defaultValue,
            @Size(max = 64) String minValue,
            @Size(max = 64) String maxValue,
            boolean publicFlag,
            @Size(max = 500) String description,
            int version,
            @Size(max = 500) String changeReason) {
    }

    public record View(String key, String name, String group, String type, String value,
                       String defaultValue, String minValue, String maxValue, String effectType,
                       boolean publicFlag, boolean builtIn, int version, String description) {
    }
}
