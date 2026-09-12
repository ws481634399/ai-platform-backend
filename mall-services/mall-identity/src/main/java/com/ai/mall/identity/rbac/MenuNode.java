package com.ai.mall.identity.rbac;

import java.util.List;

public record MenuNode(long id, Long parentId, String name, String type, String path, String componentKey,
                       String permissionCode, int sortOrder, boolean visible, List<MenuNode> children) {
    public MenuNode(long id, Long parentId, String name, String path, String componentKey,
                    String permissionCode, int sortOrder, boolean visible, List<MenuNode> children) {
        this(id, parentId, name, "PAGE", path, componentKey, permissionCode, sortOrder, visible, children);
    }
    public MenuNode {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("menu name is required");
        }
        type = type == null ? "PAGE" : type;
        if ("PAGE".equals(type) && (path == null || !path.startsWith("/") || path.startsWith("//")
                || componentKey == null || componentKey.isBlank())) {
            throw new IllegalArgumentException("PAGE requires an internal path and componentKey");
        }
        children = children == null ? List.of() : List.copyOf(children);
    }
}
