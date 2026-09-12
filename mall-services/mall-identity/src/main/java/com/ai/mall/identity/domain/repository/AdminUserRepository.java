package com.ai.mall.identity.domain.repository;

import com.ai.mall.identity.domain.model.admin.AdminUser;
import java.util.Collection;
import java.util.Optional;

public interface AdminUserRepository {
    Optional<AdminUser> findByAccount(String normalizedAccount);
    Optional<AdminUser> findById(long id);
    AdminUser add(AdminUser admin);
    void save(AdminUser admin);
    void replaceRoles(AdminUser admin);
    boolean isSuperAdmin(long id);
    long countEnabledSuperAdmins();
}
