package com.ai.mall.identity.domain.model.rbac;
public record PermissionCode(String value){public PermissionCode{{value=value==null?"":value.trim();if(!value.matches("[a-z][a-z0-9_-]*:[a-z0-9:_-]+"))throw new IllegalArgumentException("invalid permission code");}}}
