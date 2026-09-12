package com.ai.mall.identity.rbac;

import java.util.regex.Pattern;

public record RoleCode(String value) {
    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    public RoleCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("role code must be uppercase snake case");
        }
    }
}
