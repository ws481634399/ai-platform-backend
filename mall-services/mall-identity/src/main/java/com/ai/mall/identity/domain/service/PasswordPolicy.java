package com.ai.mall.identity.domain.service;

public final class PasswordPolicy {
    public void ensureStrong(String value) {
        if (value == null || value.length() < 12 || !value.matches(".*[A-Z].*")
                || !value.matches(".*[a-z].*") || !value.matches(".*\\d.*")) {
            throw new IllegalArgumentException("password must be at least 12 characters and include upper, lower and digit");
        }
    }
}
