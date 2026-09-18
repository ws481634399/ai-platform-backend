package com.ai.mall.system.domain.config;

import java.math.BigDecimal;

/**
 * 系统参数类型（CHG-0022）：保存时强类型校验 + min/max 范围校验。
 */
public enum ConfigType {

    STRING,
    INTEGER,
    LONG,
    DECIMAL,
    BOOLEAN,
    JSON;

    /**
     * 校验字面值是否符合本类型；数值类型额外校验 [min,max]（边界可空）。
     *
     * @throws IllegalArgumentException 类型/范围非法（由应用层转 B0601）
     */
    public void validate(String value, String min, String max) {
        if (value == null) {
            throw new IllegalArgumentException("参数值不能为空");
        }
        switch (this) {
            case STRING -> {
                // 长度由列约束兜底，这里不限制
            }
            case INTEGER -> {
                int parsed;
                try {
                    parsed = Integer.parseInt(value.trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("参数值不是合法 INTEGER: " + value);
                }
                checkRange(new BigDecimal(parsed), min, max);
            }
            case LONG -> {
                long parsed;
                try {
                    parsed = Long.parseLong(value.trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("参数值不是合法 LONG: " + value);
                }
                checkRange(new BigDecimal(parsed), min, max);
            }
            case DECIMAL -> {
                BigDecimal parsed;
                try {
                    parsed = new BigDecimal(value.trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("参数值不是合法 DECIMAL: " + value);
                }
                checkRange(parsed, min, max);
            }
            case BOOLEAN -> {
                String raw = value.trim();
                if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw)) {
                    throw new IllegalArgumentException("BOOLEAN 参数值只能是 true/false: " + value);
                }
            }
            case JSON -> {
                String raw = value.trim();
                if (!raw.startsWith("{") && !raw.startsWith("[")) {
                    throw new IllegalArgumentException("JSON 参数值必须是对象或数组: " + abbreviate(raw));
                }
                try {
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
                } catch (Exception ex) {
                    throw new IllegalArgumentException("JSON 参数值格式非法: " + abbreviate(raw));
                }
            }
        }
    }

    private static void checkRange(BigDecimal parsed, String min, String max) {
        if (min != null && !min.isBlank() && parsed.compareTo(new BigDecimal(min.trim())) < 0) {
            throw new IllegalArgumentException("参数值 " + parsed + " 小于下限 " + min.trim());
        }
        if (max != null && !max.isBlank() && parsed.compareTo(new BigDecimal(max.trim())) > 0) {
            throw new IllegalArgumentException("参数值 " + parsed + " 大于上限 " + max.trim());
        }
    }

    private static String abbreviate(String raw) {
        return raw.length() <= 32 ? raw : raw.substring(0, 32) + "...";
    }
}
