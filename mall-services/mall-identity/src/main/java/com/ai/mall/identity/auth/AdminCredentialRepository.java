package com.ai.mall.identity.auth;

import java.util.Optional;

public interface AdminCredentialRepository {
    Optional<AdminCredential> findByUsername(String normalizedUsername);
    Optional<AdminCredential> findById(long id);

    AdminCredential save(AdminCredential credential);
}
