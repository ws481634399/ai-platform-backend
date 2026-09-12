package com.ai.mall.identity.application.service;

import com.ai.mall.identity.application.port.PasswordHasher;
import com.ai.mall.identity.domain.model.admin.AdminAccount;
import com.ai.mall.identity.domain.repository.AdminUserRepository;
import com.ai.mall.identity.domain.exception.DomainRuleViolation;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthenticationApplicationService {
    private final AdminUserRepository admins;
    private final PasswordHasher passwords;
    public AdminAuthenticationApplicationService(AdminUserRepository admins, PasswordHasher passwords) {
        this.admins = admins; this.passwords = passwords;
    }
    public Optional<AuthenticatedAdmin> authenticate(String username, String password) {
        if (password == null || password.length() < 8 || password.length() > 128) return Optional.empty();
        final String account;
        try { account = new AdminAccount(username).value(); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
        return admins.findByAccount(account).filter(admin -> {
            try { admin.ensureCanSignIn(); return true; }
            catch (DomainRuleViolation ignored) { return false; }
        }).filter(admin -> passwords.matches(password, admin.passwordHash()))
          .map(admin -> new AuthenticatedAdmin(admin.id(), admin.account(), admin.authVersion(), admin.permissionVersion()));
    }
    public record AuthenticatedAdmin(long id, String username, long authVersion, long permissionVersion) {}
}
