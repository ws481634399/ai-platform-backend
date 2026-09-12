package com.ai.mall.identity.domain.model.rbac;
public record RoleCode(String value) { public RoleCode { value=value==null?"":value.trim().toUpperCase(); if(!value.matches("[A-Z][A-Z0-9_]{2,63}"))throw new IllegalArgumentException("invalid role code"); } }
