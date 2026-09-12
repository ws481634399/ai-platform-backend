package com.ai.mall.identity.application.dto;

import java.util.List;
import java.util.Set;

public record AuthorizationSnapshot(long adminId, long permissionVersion,
                                    Set<String> permissions, List<MenuNode> menus) {
    public AuthorizationSnapshot {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        menus = menus == null ? List.of() : List.copyOf(menus);
    }

    public boolean permits(String code) {
        return permissions.contains(code);
    }
}
