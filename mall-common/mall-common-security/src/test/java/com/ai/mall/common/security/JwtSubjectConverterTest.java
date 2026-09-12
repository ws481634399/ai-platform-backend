package com.ai.mall.common.security;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtSubjectConverterTest {
    @Test @org.junit.jupiter.api.DisplayName("STORY-001-01-03-01/TC-001 and STORY-001-01-03-02/TC-001 typed ADMIN principal") void createsTypedAdminPrincipalAndAuthorities() {
        var authentication = new JwtSubjectConverter().convert(jwt(Map.of("sub", "7", "subject_type", "ADMIN",
                "username", "alice", "auth_version", 2, "permissions", List.of("admin:read"))));
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedSubject("7", "alice", SubjectType.ADMIN, 2, java.util.Set.of("admin:read")));
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactlyInAnyOrder("ROLE_ADMIN", "admin:read");
    }

    @Test @org.junit.jupiter.api.DisplayName("STORY-001-01-03-01/TC-002..003 and STORY-001-01-03-02/TC-002..003 default rejection") void rejectsMissingUnknownAndGuestSubjectTypes() {
        var converter = new JwtSubjectConverter();
        assertThatThrownBy(() -> converter.convert(jwt(Map.of("sub", "7", "auth_version", 1)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> converter.convert(jwt(Map.of("sub", "7", "subject_type", "ALIEN", "auth_version", 1)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> converter.convert(jwt(Map.of("sub", "7", "subject_type", "GUEST", "auth_version", 1)))).isInstanceOf(IllegalArgumentException.class);
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"), claims);
    }
}
