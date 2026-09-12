package com.ai.mall.identity.application.service;

import com.ai.mall.identity.application.exception.UseCaseException;
import com.ai.mall.identity.application.port.AuditRecorder;
import com.ai.mall.identity.application.port.AdminUserQuery;
import com.ai.mall.identity.application.port.PasswordHasher;
import com.ai.mall.identity.domain.model.admin.AdminUser;
import com.ai.mall.identity.domain.exception.DuplicateResourceException;
import com.ai.mall.identity.domain.model.admin.AdminUserStatus;
import com.ai.mall.identity.domain.repository.AdminUserRepository;
import com.ai.mall.identity.domain.service.PasswordPolicy;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserApplicationService {
    private final AdminUserRepository admins;
    private final PasswordHasher passwords;
    private final AuditRecorder audit;
    private final AdminUserQuery query;
    private final PasswordPolicy policy = new PasswordPolicy();
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public AdminUserApplicationService(AdminUserRepository admins, AdminUserQuery query, PasswordHasher passwords, AuditRecorder audit) {
        this(admins, query, passwords, audit, Clock.systemUTC());
    }
    AdminUserApplicationService(AdminUserRepository admins, AdminUserQuery query, PasswordHasher passwords, AuditRecorder audit, Clock clock) {
        this.admins = admins; this.query = query; this.passwords = passwords; this.audit = audit; this.clock = clock;
    }
    @Transactional public AdminView create(long actor, String username, String rawPassword) {
        policy.ensureStrong(rawPassword);
        try {
            var saved = admins.add(AdminUser.create(username, passwords.hash(rawPassword), clock.instant()));
            audit.record(actor, "ADMIN_CREATE", "ADMIN", Long.toString(saved.id()), "SUCCESS", null);
            return view(saved);
        } catch (DuplicateResourceException ex) {
            audit.record(actor, "ADMIN_CREATE", "ADMIN", "NEW", "FAILURE", "DUPLICATE_USERNAME");
            throw conflict("username already exists");
        }
    }
    public Page page(long page, int size) {
        if (page < 1 || size < 1 || size > 200) throw bad("invalid page");
        return new Page(query.count(), query.page((page - 1) * size, size));
    }
    @Transactional public void changeStatus(long actor, long id, AdminUserStatus status) {
        var admin = admins.findById(id).orElseThrow(() -> notFound("admin not found"));
        if (admin.status() == status) return;
        if (status != AdminUserStatus.ENABLED && admins.isSuperAdmin(id) && admins.countEnabledSuperAdmins() <= 1) {
            audit.record(actor, "ADMIN_DISABLE", "ADMIN", Long.toString(id), "FAILURE", "LAST_SUPER_ADMIN");
            throw conflict("last enabled super administrator cannot be disabled");
        }
        admin.changeStatus(status, clock.instant());
        admins.save(admin);
        audit.record(actor, "ADMIN_STATUS_CHANGE", "ADMIN", Long.toString(id), "SUCCESS", null);
    }
    @Transactional public void changePassword(long actor, long id, String rawPassword) {
        policy.ensureStrong(rawPassword);
        var admin = admins.findById(id).orElseThrow(() -> notFound("admin not found"));
        admin.resetPassword(passwords.hash(rawPassword), clock.instant());
        admins.save(admin);
        audit.record(actor, "ADMIN_PASSWORD_CHANGE", "ADMIN", Long.toString(id), "SUCCESS", null);
    }
    private static AdminView view(AdminUser admin) { return new AdminView(admin.id(), admin.account(), admin.status().name(), admin.authVersion(), admin.permissionVersion()); }
    private static UseCaseException bad(String m) { return new UseCaseException(UseCaseException.Kind.INVALID, m); }
    private static UseCaseException notFound(String m) { return new UseCaseException(UseCaseException.Kind.NOT_FOUND, m); }
    private static UseCaseException conflict(String m) { return new UseCaseException(UseCaseException.Kind.CONFLICT, m); }
    public record AdminView(long id, String username, String status, long authVersion, long permissionVersion) {}
    public record Page(long total, List<AdminUserQuery.Summary> items) {}
}
