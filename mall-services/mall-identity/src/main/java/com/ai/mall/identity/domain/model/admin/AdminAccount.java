package com.ai.mall.identity.domain.model.admin;

public record AdminAccount(String value) {
    public AdminAccount {
        value = value == null ? "" : value.trim().toLowerCase();
        if (value.length() < 3 || value.length() > 64 || !value.matches("[a-z0-9._@-]+")) {
            throw new IllegalArgumentException("invalid administrator account");
        }
    }
}
