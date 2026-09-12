package com.ai.mall.identity.rbac;

import java.util.regex.Pattern;

public record PermissionCode(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9-]*:[a-z][a-z0-9-]*");
    public PermissionCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("permission code must match <resource>:<action>");
        }
    }
}
