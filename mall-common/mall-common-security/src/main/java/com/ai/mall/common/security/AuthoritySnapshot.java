package com.ai.mall.common.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;

/**
 * 下游资源服务视角的授权快照（与 mall-identity 写入 Redis 的 JSON 结构对齐）。
 *
 * <p>仅反序列化权限判定所需字段；menus 等会话引导字段通过 {@link JsonIgnoreProperties} 忽略。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthoritySnapshot(Long adminId, Long permissionVersion, Set<String> permissions) {

    public AuthoritySnapshot {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
