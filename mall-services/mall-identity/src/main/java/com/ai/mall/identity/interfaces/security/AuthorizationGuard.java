package com.ai.mall.identity.interfaces.security;

import com.ai.mall.common.security.SecurityContextFacade;
import com.ai.mall.identity.application.service.AuthorizationDecisionService;
import org.springframework.stereotype.Component;

@Component("authorizationGuard")
public class AuthorizationGuard {
    private final AuthorizationDecisionService authorization;
    public AuthorizationGuard(AuthorizationDecisionService authorization) {
        this.authorization = authorization;
    }
    public boolean has(String permission) {
        var subject = SecurityContextFacade.currentSubject().orElse(null);
        if (subject == null || !subject.isAdmin()) return false;
        long id;
        try { id = Long.parseLong(subject.subjectId()); } catch (NumberFormatException ignored) { return false; }
        return authorization.permits(id, permission);
    }
}
