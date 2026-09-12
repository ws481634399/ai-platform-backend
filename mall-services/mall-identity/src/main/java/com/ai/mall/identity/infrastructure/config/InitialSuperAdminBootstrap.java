package com.ai.mall.identity.infrastructure.config;

import com.ai.mall.identity.application.port.AuditRecorder;
import com.ai.mall.identity.application.port.PasswordHasher;
import com.ai.mall.identity.domain.model.admin.AdminAccount;
import com.ai.mall.identity.domain.model.admin.AdminUser;
import com.ai.mall.identity.domain.repository.AdminUserRepository;
import com.ai.mall.identity.infrastructure.persistence.rbac.RbacCommandMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "mall.security.bootstrap.enabled", havingValue = "true")
public class InitialSuperAdminBootstrap implements ApplicationRunner {
    private final AdminUserRepository admins;
    private final RbacCommandMapper rbac;
    private final PasswordHasher passwords;
    private final AuditRecorder audit;
    private final String username;
    private final String password;

    public InitialSuperAdminBootstrap(AdminUserRepository admins, RbacCommandMapper rbac, PasswordHasher passwords, AuditRecorder audit,
            @Value("${mall.security.bootstrap.username}") String username,
            @Value("${mall.security.bootstrap.password}") String password) {
        this.admins = admins; this.rbac = rbac; this.passwords = passwords; this.audit = audit; this.username = username; this.password = password;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        String normalized = new AdminAccount(username).value();
        if (normalized.isBlank() || password.length() < 12) throw new IllegalStateException("bootstrap admin requires username and 12+ character password");
        if (admins.findByAccount(normalized).isPresent()) return;
        var saved = admins.add(AdminUser.create(normalized, passwords.hash(password), java.time.Instant.now()));
        Long roleId = rbac.findRoleIdByCode("SUPER_ADMIN");
        if (roleId == null) throw new IllegalStateException("SUPER_ADMIN seed is missing");
        saved.replaceRoles(java.util.List.of(roleId), java.time.Instant.now());
        admins.replaceRoles(saved);
        audit.record(saved.id(), "INITIAL_SUPER_ADMIN_CREATE", "ADMIN", Long.toString(saved.id()), "SUCCESS", null);
    }
}
