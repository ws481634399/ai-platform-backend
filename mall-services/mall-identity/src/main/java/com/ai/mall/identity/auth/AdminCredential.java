package com.ai.mall.identity.auth;

import java.time.Instant;

public record AdminCredential(
        long id,
        String username,
        String passwordHash,
        AdminStatus status,
        long authVersion,
        long permissionVersion,
        Instant createdAt,
        Instant updatedAt) {

    public AdminCredential {
        username = normalizeUsername(username);
        if (username.length() < 3 || username.length() > 64) {
            throw new IllegalArgumentException("username length must be between 3 and 64");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    public boolean canAuthenticate() {
        return status == AdminStatus.ENABLED;
    }
}
