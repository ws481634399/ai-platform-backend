package com.ai.mall.system.domain.config;

import java.time.Instant;

/**
 * 类型化系统参数聚合（CHG-0022）。
 *
 * <p>更新值必须通过 {@link ConfigType#validate} 类型与范围校验；
 * 整数 version 乐观锁；config_key 创建后不可变；内置键禁止删除。
 */
public class SystemParameter {

    private Long id;
    private final String key;
    private String name;
    private String group;
    private final ConfigType type;
    private String value;
    private String defaultValue;
    private String minValue;
    private String maxValue;
    private final EffectType effectType;
    private boolean publicFlag;
    private final boolean builtIn;
    private int version;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    public SystemParameter(Long id, String key, String name, String group, ConfigType type,
                           String value, String defaultValue, String minValue, String maxValue,
                           EffectType effectType, boolean publicFlag, boolean builtIn, int version,
                           String description, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.group = group;
        this.type = type;
        this.value = value;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.effectType = effectType;
        this.publicFlag = publicFlag;
        this.builtIn = builtIn;
        this.version = version;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 管理端新建（非内置）：创建即校验值。 */
    public static SystemParameter createNew(String key, String name, String group, ConfigType type,
                                            String value, String defaultValue, String minValue, String maxValue,
                                            EffectType effectType, boolean publicFlag, String description,
                                            Instant now) {
        type.validate(value, minValue, maxValue);
        return new SystemParameter(null, key, name,
                group == null || group.isBlank() ? "default" : group,
                type, value, defaultValue, emptyToNull(minValue), emptyToNull(maxValue),
                effectType == null ? EffectType.DYNAMIC : effectType,
                publicFlag, false, 0, description, now, now);
    }

    /** 应用编辑：元数据可变，值经类型/范围校验，version+1。 */
    public SystemParameter applyEdit(String newName, String newGroup, String newValue,
                                     String newDefaultValue, String newMin, String newMax,
                                     boolean newPublicFlag, String newDescription) {
        ConfigType effectiveType = this.type;
        String min = emptyToNull(newMin);
        String max = emptyToNull(newMax);
        effectiveType.validate(newValue, min, max);
        return new SystemParameter(id, key, newName,
                newGroup == null || newGroup.isBlank() ? "default" : newGroup,
                type, newValue, newDefaultValue, min, max, effectType,
                newPublicFlag, builtIn, version + 1, newDescription, createdAt, Instant.now());
    }

    public void validateValue(String candidate) {
        type.validate(candidate, minValue, maxValue);
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    private static String emptyToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    public Long getId() {
        return id;
    }

    public void assignId(long id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getGroup() {
        return group;
    }

    public ConfigType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getMinValue() {
        return minValue;
    }

    public String getMaxValue() {
        return maxValue;
    }

    public EffectType getEffectType() {
        return effectType;
    }

    public boolean isPublicFlag() {
        return publicFlag;
    }

    public int getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
