package com.ai.mall.identity.api;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.SecurityContextFacade;
import com.ai.mall.identity.auth.AdminCredentialRepository;
import com.ai.mall.identity.rbac.AuthorizationService;
import com.ai.mall.identity.rbac.MenuNode;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/session")
@Profile("!test")
public class AdminSessionController {
    private final AdminCredentialRepository admins;
    private final AuthorizationService authorization;
    public AdminSessionController(AdminCredentialRepository admins, AuthorizationService authorization) {
        this.admins = admins; this.authorization = authorization;
    }

    @GetMapping("/bootstrap")
    public UnifyResult<BootstrapResponse> bootstrap() {
        var subject = SecurityContextFacade.requireAdmin();
        long id = Long.parseLong(subject.subjectId());
        var admin = admins.findById(id).filter(com.ai.mall.identity.auth.AdminCredential::canAuthenticate)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED));
        var snapshot = authorization.get(id, admin.permissionVersion());
        return UnifyResult.ok(new BootstrapResponse(new User(Long.toString(id), admin.username(), admin.username()), snapshot.menus(),
                snapshot.permissions(), snapshot.permissionVersion()));
    }

    public record User(String id, String username, String displayName) {}
    public record BootstrapResponse(User user, List<MenuNode> menus, Set<String> permissions, long permissionVersion) {}
}
