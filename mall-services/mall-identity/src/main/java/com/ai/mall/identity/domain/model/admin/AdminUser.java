package com.ai.mall.identity.domain.model.admin;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import com.ai.mall.identity.domain.exception.DomainRuleViolation;

/** AdminUser aggregate root. All write-side state transitions live here. */
public final class AdminUser {
    private final long id;
    private final AdminAccount account;
    private PasswordHash passwordHash;
    private AdminUserStatus status;
    private long authVersion;
    private long permissionVersion;
    private Set<Long> roleIds;
    private final Instant createdAt;
    private Instant updatedAt;

    private AdminUser(long id, AdminAccount account, PasswordHash passwordHash, AdminUserStatus status,
                      long authVersion, long permissionVersion, Collection<Long> roleIds,
                      Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.account = account;
        this.passwordHash = passwordHash;
        this.status = status;
        this.authVersion = authVersion;
        this.permissionVersion = permissionVersion;
        this.roleIds = distinctIds(roleIds);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static AdminUser create(String account, String passwordHash, Instant now) {
        return new AdminUser(0, new AdminAccount(account), new PasswordHash(passwordHash),
                AdminUserStatus.ENABLED, 1, 1, Set.of(), now, now);
    }

    public static AdminUser reconstitute(long id, String account, String passwordHash, String status,
                                         long authVersion, long permissionVersion, Collection<Long> roleIds,
                                         Instant createdAt, Instant updatedAt) {
        if (id <= 0) throw new IllegalArgumentException("admin id must be positive");
        return new AdminUser(id, new AdminAccount(account), new PasswordHash(passwordHash),
                AdminUserStatus.valueOf(status), authVersion, permissionVersion, roleIds, createdAt, updatedAt);
    }

    public void changeStatus(AdminUserStatus next, Instant now) {
        if (next == null) throw new IllegalArgumentException("status is required");
        if (status == next) return;
        status = next;
        authVersion++;
        permissionVersion++;
        updatedAt = now;
    }

    public void resetPassword(String encodedPassword, Instant now) {
        passwordHash = new PasswordHash(encodedPassword);
        authVersion++;
        updatedAt = now;
    }

    public void replaceRoles(Collection<Long> ids, Instant now) {
        roleIds = distinctIds(ids);
        permissionVersion++;
        updatedAt = now;
    }

    public void ensureCanSignIn() {
        if (status != AdminUserStatus.ENABLED) throw new DomainRuleViolation("administrator cannot sign in");
    }

    private static Set<Long> distinctIds(Collection<Long> values) {
        var result = new LinkedHashSet<Long>();
        if (values == null) return result;
        for (Long value : values) {
            if (value == null || value <= 0 || !result.add(value)) {
                throw new IllegalArgumentException("role ids must be unique and positive");
            }
        }
        return result;
    }

    public long id() { return id; }
    public String account() { return account.value(); }
    public String passwordHash() { return passwordHash.value(); }
    public AdminUserStatus status() { return status; }
    public long authVersion() { return authVersion; }
    public long permissionVersion() { return permissionVersion; }
    public Set<Long> roleIds() { return Set.copyOf(roleIds); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
