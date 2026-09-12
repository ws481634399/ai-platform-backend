package com.ai.mall.identity.rbac;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RbacValueObjectsTest {
    @Test void rejectsInvalidPermissionAndRoleCodes() {
        assertThatThrownBy(() -> new PermissionCode("Admin:Read")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoleCode("admin")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsExternalMenuPaths() {
        assertThatThrownBy(() -> new MenuNode(1, null, "bad", "//evil.example", "Bad", null, 0, true, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
