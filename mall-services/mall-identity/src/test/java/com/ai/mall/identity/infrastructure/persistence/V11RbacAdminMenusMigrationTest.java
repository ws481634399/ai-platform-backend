package com.ai.mall.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * CHG-0023 A1：V11 RBAC 管理台菜单种子迁移验证。
 * 只读校验 Flyway 已提交数据（不加 @Transactional，不产生任何测试写入）。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("V11 RBAC 管理台菜单种子迁移")
class V11RbacAdminMenusMigrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("权限管理目录与三个管理页菜单存在且 component_key/权限码正确")
    void rbacAdminMenusSeedExists() {
        List<String> paths = jdbc.queryForList(
                "SELECT path FROM auth_menu WHERE path IN "
                        + "('/security','/security/admins','/security/roles','/security/menus') ORDER BY path",
                String.class);
        assertThat(paths).containsExactly(
                "/security", "/security/admins", "/security/menus", "/security/roles");

        assertThat(menuColumn("/security/admins", "component_key")).isEqualTo("SecurityAdmins");
        assertThat(menuColumn("/security/admins", "permission_code")).isEqualTo("admin:read");
        assertThat(menuColumn("/security/roles", "component_key")).isEqualTo("SecurityRoles");
        assertThat(menuColumn("/security/roles", "permission_code")).isEqualTo("role:read");
        assertThat(menuColumn("/security/menus", "component_key")).isEqualTo("SecurityMenus");
        assertThat(menuColumn("/security/menus", "permission_code")).isEqualTo("menu:read");
        assertThat(menuColumn("/security", "type")).isEqualTo("DIRECTORY");
    }

    @Test
    @DisplayName("超管角色被授予目录与三个页面（bootstrap 菜单树可装配）")
    void superAdminAuthorizedForRbacMenus() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_role_menu rm "
                        + "JOIN auth_role r ON r.id = rm.role_id "
                        + "JOIN auth_menu m ON m.id = rm.menu_id "
                        + "WHERE r.code = 'SUPER_ADMIN' AND m.path IN "
                        + "('/security','/security/admins','/security/roles','/security/menus')",
                Long.class);
        assertThat(count).isEqualTo(4L);
    }

    private String menuColumn(String path, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM auth_menu WHERE path = ?", String.class, path);
    }
}
