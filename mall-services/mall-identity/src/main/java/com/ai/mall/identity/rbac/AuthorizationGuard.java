package com.ai.mall.identity.rbac;

import com.ai.mall.common.security.SecurityContextFacade;
import com.ai.mall.identity.auth.AdminCredentialRepository;
import org.springframework.stereotype.Component;

@Component("authorizationGuard")
public class AuthorizationGuard {
    private final AdminCredentialRepository admins;
    private final AuthorizationService authorization;
    public AuthorizationGuard(AdminCredentialRepository admins, AuthorizationService authorization) {
        this.admins = admins; this.authorization = authorization;
    }
    public boolean has(String permission) {
        var subject = SecurityContextFacade.currentSubject().orElse(null);
        if (subject == null || !subject.isAdmin()) return false;
        long id;
        try { id = Long.parseLong(subject.subjectId()); } catch (NumberFormatException ignored) { return false; }
        return admins.findById(id).filter(com.ai.mall.identity.auth.AdminCredential::canAuthenticate)
                .map(admin -> authorization.get(id, admin.permissionVersion()).permits(permission)).orElse(false);
    }
}
