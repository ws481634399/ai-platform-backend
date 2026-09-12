package com.ai.mall.identity.config;

import com.ai.mall.identity.auth.*;
import com.ai.mall.identity.persistence.RbacCommandMapper;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "mall.security.bootstrap.enabled", havingValue = "true")
public class InitialSuperAdminBootstrap implements ApplicationRunner {
    private final AdminCredentialRepository admins;
    private final RbacCommandMapper rbac;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public InitialSuperAdminBootstrap(AdminCredentialRepository admins, RbacCommandMapper rbac, PasswordEncoder encoder,
            @Value("${mall.security.bootstrap.username}") String username,
            @Value("${mall.security.bootstrap.password}") String password) {
        this.admins = admins; this.rbac = rbac; this.encoder = encoder; this.username = username; this.password = password;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        String normalized = AdminCredential.normalizeUsername(username);
        if (normalized.isBlank() || password.length() < 12) throw new IllegalStateException("bootstrap admin requires username and 12+ character password");
        if (admins.findByUsername(normalized).isPresent()) return;
        var saved = admins.save(new AdminCredential(0, normalized, encoder.encode(password), AdminStatus.ENABLED,
                1, 1, Instant.now(), Instant.now()));
        Long roleId = rbac.findRoleIdByCode("SUPER_ADMIN");
        if (roleId == null) throw new IllegalStateException("SUPER_ADMIN seed is missing");
        rbac.addAdminRoles(saved.id(), java.util.List.of(roleId));
        rbac.bumpAdminPermissionVersion(saved.id());
        rbac.audit(saved.id(), "INITIAL_SUPER_ADMIN_CREATE", "ADMIN", Long.toString(saved.id()), "SUCCESS", null, "{}");
    }
}
