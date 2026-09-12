package com.ai.mall.identity.persistence;

import com.ai.mall.identity.auth.AdminCredential;
import com.ai.mall.identity.auth.AdminCredentialRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAdminCredentialRepository implements AdminCredentialRepository {
    private final AdminCredentialMapper mapper;

    public MyBatisAdminCredentialRepository(AdminCredentialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AdminCredential> findByUsername(String normalizedUsername) {
        return Optional.ofNullable(mapper.findByUsername(normalizedUsername));
    }
    @Override public Optional<AdminCredential> findById(long id) { return Optional.ofNullable(mapper.findById(id)); }

    @Override
    public AdminCredential save(AdminCredential credential) {
        AdminCredentialRow row = new AdminCredentialRow();
        row.setUsername(credential.username());
        row.setPasswordHash(credential.passwordHash());
        row.setStatus(credential.status().name());
        row.setAuthVersion(credential.authVersion());
        row.setPermissionVersion(credential.permissionVersion());
        mapper.insert(row);
        return new AdminCredential(row.getId(), credential.username(), credential.passwordHash(), credential.status(),
                credential.authVersion(), credential.permissionVersion(), credential.createdAt(), credential.updatedAt());
    }
}
