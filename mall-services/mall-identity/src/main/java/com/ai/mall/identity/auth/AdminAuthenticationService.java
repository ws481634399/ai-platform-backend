package com.ai.mall.identity.auth;

import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthenticationService {
    private final AdminCredentialRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthenticationService(AdminCredentialRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<AuthenticatedAdmin> authenticate(String username, String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            return Optional.empty();
        }
        return repository.findByUsername(AdminCredential.normalizeUsername(username))
                .filter(AdminCredential::canAuthenticate)
                .filter(credential -> passwordEncoder.matches(password, credential.passwordHash()))
                .map(credential -> new AuthenticatedAdmin(
                        credential.id(), credential.username(), credential.authVersion(), credential.permissionVersion()));
    }

    public record AuthenticatedAdmin(long id, String username, long authVersion, long permissionVersion) {
    }
}
