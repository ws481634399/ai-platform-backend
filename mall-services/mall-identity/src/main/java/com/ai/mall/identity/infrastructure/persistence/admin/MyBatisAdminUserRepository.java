package com.ai.mall.identity.infrastructure.persistence.admin;

import com.ai.mall.identity.domain.model.admin.AdminUser;
import com.ai.mall.identity.domain.exception.DuplicateResourceException;
import com.ai.mall.identity.domain.repository.AdminUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAdminUserRepository implements AdminUserRepository {
    private final AdminUserMapper mapper;
    public MyBatisAdminUserRepository(AdminUserMapper mapper) { this.mapper = mapper; }
    @Override public Optional<AdminUser> findByAccount(String account) { return Optional.ofNullable(mapper.findByAccount(account)).map(this::toDomain); }
    @Override public Optional<AdminUser> findById(long id) { return Optional.ofNullable(mapper.findById(id)).map(this::toDomain); }
    @Override public AdminUser add(AdminUser admin) {
        var po = toPo(admin); try { mapper.insert(po); } catch (org.springframework.dao.DuplicateKeyException ex) { throw new DuplicateResourceException("administrator account already exists", ex); }
        return AdminUser.reconstitute(po.getId(), admin.account(), admin.passwordHash(), admin.status().name(),
                admin.authVersion(), admin.permissionVersion(), admin.roleIds(), admin.createdAt(), admin.updatedAt());
    }
    @Override public void save(AdminUser admin) {
        if (mapper.update(toPo(admin)) != 1) throw new IllegalStateException("admin not found");
    }
    @Override public void replaceRoles(AdminUser admin) {
        mapper.clearRoles(admin.id());
        if (!admin.roleIds().isEmpty()) mapper.addRoles(admin.id(), admin.roleIds());
        save(admin);
    }
    @Override public boolean isSuperAdmin(long id) { return mapper.isSuperAdmin(id) > 0; }
    @Override public long countEnabledSuperAdmins() { return mapper.countEnabledSuperAdmins(); }
    private AdminUser toDomain(AdminUserPo po) {
        Instant created = po.getCreatedAt() == null ? Instant.EPOCH : po.getCreatedAt();
        Instant updated = po.getUpdatedAt() == null ? created : po.getUpdatedAt();
        return AdminUser.reconstitute(po.getId(), po.getUsername(), po.getPasswordHash(), po.getStatus(),
                po.getAuthVersion(), po.getPermissionVersion(), mapper.findRoleIds(po.getId()), created, updated);
    }
    private static AdminUserPo toPo(AdminUser admin) {
        var po = new AdminUserPo(); po.setId(admin.id()); po.setUsername(admin.account());
        po.setPasswordHash(admin.passwordHash()); po.setStatus(admin.status().name());
        po.setAuthVersion(admin.authVersion()); po.setPermissionVersion(admin.permissionVersion());
        po.setCreatedAt(admin.createdAt()); po.setUpdatedAt(admin.updatedAt()); return po;
    }
}
