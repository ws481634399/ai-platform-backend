package com.ai.mall.identity.domain.model.admin;

public record PasswordHash(String value) {
    public PasswordHash {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("password hash is required");
    }
}
