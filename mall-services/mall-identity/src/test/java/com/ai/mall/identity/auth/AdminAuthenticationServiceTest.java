package com.ai.mall.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminAuthenticationServiceTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AdminCredential credential;
    private AdminAuthenticationService service;

    @BeforeEach
    void setUp() {
        credential = new AdminCredential(1, "admin", encoder.encode("correct-pass"), AdminStatus.ENABLED,
                2, 3, Instant.EPOCH, Instant.EPOCH);
        AdminCredentialRepository repository = new AdminCredentialRepository() {
            @Override public Optional<AdminCredential> findByUsername(String username) {
                return credential.username().equals(username) ? Optional.of(credential) : Optional.empty();
            }
            @Override public Optional<AdminCredential> findById(long id) { return id == credential.id() ? Optional.of(credential) : Optional.empty(); }
            @Override public AdminCredential save(AdminCredential value) { credential = value; return value; }
        };
        service = new AdminAuthenticationService(repository, encoder);
    }

    @Test
    void authenticatesEnabledAdminWithoutExposingHash() {
        var result = service.authenticate(" ADMIN ", "correct-pass");
        assertThat(result).contains(new AdminAuthenticationService.AuthenticatedAdmin(1, "admin", 2, 3));
        assertThat(result.orElseThrow().toString()).doesNotContain("correct-pass", credential.passwordHash());
    }

    @Test
    void returnsSameEmptyResultForUnknownWrongOrDisabledAccount() {
        assertThat(service.authenticate("missing", "correct-pass")).isEmpty();
        assertThat(service.authenticate("admin", "wrong-pass")).isEmpty();
        credential = new AdminCredential(1, "admin", credential.passwordHash(), AdminStatus.DISABLED,
                2, 3, Instant.EPOCH, Instant.EPOCH);
        assertThat(service.authenticate("admin", "correct-pass")).isEmpty();
    }
}
