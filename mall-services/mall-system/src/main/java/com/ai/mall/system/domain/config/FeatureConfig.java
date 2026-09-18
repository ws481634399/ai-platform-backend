package com.ai.mall.system.domain.config;

import java.time.Instant;

/**
 * 功能开关聚合（CHG-0022）。
 *
 * <p>config_key 创建后不可变；内置键（builtIn）禁止删除；更新走整数 version 乐观锁。
 */
public class FeatureConfig {

    private Long id;
    private final String key;
    private String name;
    private String group;
    private boolean enabled;
    private boolean publicFlag;
    private final boolean builtIn;
    private int version;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    public FeatureConfig(Long id, String key, String name, String group, boolean enabled,
                         boolean publicFlag, boolean builtIn, int version, String description,
                         Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.group = group;
        this.enabled = enabled;
        this.publicFlag = publicFlag;
        this.builtIn = builtIn;
        this.version = version;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 管理端新建（非内置，version=0）。 */
    public static FeatureConfig createNew(String key, String name, String group, boolean enabled,
                                          boolean publicFlag, String description, Instant now) {
        return new FeatureConfig(null, key, name, group == null || group.isBlank() ? "default" : group,
                enabled, publicFlag, false, 0, description, now, now);
    }

    /** 应用编辑：内置键只允许改值/名称等内容，key 永不改变（路径参数即键）。 */
    public FeatureConfig applyEdit(String newName, String newGroup, boolean newEnabled,
                                   boolean newPublicFlag, String newDescription) {
        return new FeatureConfig(id, key, newName,
                newGroup == null || newGroup.isBlank() ? "default" : newGroup,
                newEnabled, newPublicFlag, builtIn, version + 1, newDescription, createdAt, Instant.now());
    }

    public boolean isBuiltIn() {
        return builtIn;
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

    public boolean isEnabled() {
        return enabled;
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
