package com.ai.mall.common.security;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;
import java.util.Set;

public record AuthenticatedSubject(
        String subjectId,
        String username,
        SubjectType subjectType,
        long authVersion,
        Set<String> permissions) implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthenticatedSubject {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId must not be blank");
        }
        Objects.requireNonNull(subjectType, "subjectType");
        username = username == null ? "" : username;
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    @Override
    public String getName() {
        return subjectId;
    }

    public boolean isAdmin() {
        return subjectType == SubjectType.ADMIN;
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
