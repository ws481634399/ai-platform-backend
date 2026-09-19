package com.ai.mall.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import com.ai.mall.identity.application.port.AdminUserQuery;
import com.ai.mall.identity.domain.model.admin.AdminUser;
import com.ai.mall.identity.domain.model.admin.AdminUserStatus;
import com.ai.mall.identity.domain.model.rbac.Role;
import com.ai.mall.identity.domain.repository.AdminUserRepository;
import com.ai.mall.identity.domain.repository.RoleRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IdentityRepositoryIntegrationTest {
    @Autowired AdminUserRepository admins;
    @Autowired AdminUserQuery adminQuery;
    @Autowired RoleRepository roles;

    @Test void reconstructsAdminAggregateAndPersistsItsTransitions() {
        var saved = admins.add(AdminUser.create("ddd_admin", "bcrypt-hash", Instant.now()));
        long roleId = roles.findAll().stream().filter(role -> role.code().equals("SUPER_ADMIN")).findFirst().orElseThrow().id();
        saved.replaceRoles(List.of(roleId), Instant.now());
        saved.changeStatus(AdminUserStatus.DISABLED, Instant.now());
        admins.replaceRoles(saved);

        var loaded = admins.findById(saved.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(AdminUserStatus.DISABLED);
        assertThat(loaded.roleIds()).containsExactly(roleId);
        var page = adminQuery.page(0, 20);
        assertThat(page).anyMatch(row -> row.id() == saved.id());
        // CHG-0023 A1：分页视图携带角色 id 供管理台回显
        var row = page.stream().filter(r -> r.id() == saved.id()).findFirst().orElseThrow();
        assertThat(row.roleIds()).containsExactly(roleId);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("CHG-0023 A1：无角色管理员分页行 roleIds 为空列表而非 null")
    void adminWithoutRolesHasEmptyRoleIdsInPage() {
        var saved = admins.add(AdminUser.create("no_role_admin", "bcrypt-hash", Instant.now()));
        var row = adminQuery.page(0, 50).stream().filter(r -> r.id() == saved.id()).findFirst().orElseThrow();
        assertThat(row.roleIds()).isNotNull().isEmpty();
    }

    @Test void mapsRolePersistenceRowsBackToCompleteAggregate() {
        roles.add(Role.create("OPS_ADMIN", "Operations", "operations administrators"));
        assertThat(roles.findAll()).anyMatch(role -> role.code().equals("OPS_ADMIN") && role.name().equals("Operations"));
    }
}
