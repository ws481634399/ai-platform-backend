package com.ai.mall.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthenticatedSubjectTest {
    @Test
    void isolatesSubjectTypesAndPermissions() {
        var admin = new AuthenticatedSubject("42", "root", SubjectType.ADMIN, 3, Set.of("admin:read"));

        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.hasPermission("admin:read")).isTrue();
        assertThat(admin.hasPermission("admin:write")).isFalse();
    }

    @Test
    void rejectsBlankSubjectId() {
        assertThatThrownBy(() -> new AuthenticatedSubject(" ", "root", SubjectType.ADMIN, 1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
