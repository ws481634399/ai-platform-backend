package com.ai.mall.common.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityContextFacade {
    private SecurityContextFacade() {
    }

    public static Optional<AuthenticatedSubject> currentSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AuthenticatedSubject subject
                ? Optional.of(subject)
                : Optional.empty();
    }

    public static AuthenticatedSubject requireAdmin() {
        AuthenticatedSubject subject = currentSubject()
                .orElseThrow(() -> new IllegalStateException("authenticated subject required"));
        if (!subject.isAdmin()) {
            throw new IllegalStateException("admin subject required");
        }
        return subject;
    }
}
