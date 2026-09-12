package com.ai.mall.identity.infrastructure.security;

import com.ai.mall.identity.application.port.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public final class BCryptPasswordHasher implements PasswordHasher {
    private final PasswordEncoder encoder;
    public BCryptPasswordHasher(PasswordEncoder encoder) { this.encoder = encoder; }
    @Override public String hash(String rawPassword) { return encoder.encode(rawPassword); }
    @Override public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
