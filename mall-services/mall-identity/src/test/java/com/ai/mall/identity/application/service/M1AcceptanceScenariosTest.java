package com.ai.mall.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.identity.application.dto.AuthorizationSnapshot;
import com.ai.mall.identity.application.dto.MenuNode;
import com.ai.mall.identity.application.exception.UseCaseException;
import com.ai.mall.identity.application.port.AdminUserQuery;
import com.ai.mall.identity.application.port.AuditRecorder;
import com.ai.mall.identity.application.port.AuthorizationCache;
import com.ai.mall.identity.application.port.AuthorizationRepository;
import com.ai.mall.identity.application.port.PasswordHasher;
import com.ai.mall.identity.domain.exception.DuplicateResourceException;
import com.ai.mall.identity.domain.model.admin.AdminUser;
import com.ai.mall.identity.domain.model.admin.AdminUserStatus;
import com.ai.mall.identity.domain.model.rbac.Menu;
import com.ai.mall.identity.domain.model.rbac.Permission;
import com.ai.mall.identity.domain.model.rbac.Role;
import com.ai.mall.identity.domain.model.session.RefreshSession;
import com.ai.mall.identity.domain.repository.AdminUserRepository;
import com.ai.mall.identity.domain.repository.MenuRepository;
import com.ai.mall.identity.domain.repository.PermissionRepository;
import com.ai.mall.identity.domain.repository.RefreshSessionRepository;
import com.ai.mall.identity.domain.repository.RoleRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class M1AcceptanceScenariosTest {
    private static final Instant NOW = Instant.parse("2026-09-12T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("STORY-001-02-01-01/TC-001..003 administrator lifecycle and lockout protection")
    void administratorLifecycle() {
        var admins = mock(AdminUserRepository.class);
        var query = mock(AdminUserQuery.class);
        var passwords = mock(PasswordHasher.class);
        var audit = mock(AuditRecorder.class);
        when(passwords.hash("StrongPassword1")).thenReturn("bcrypt-hash");
        when(admins.add(any())).thenAnswer(invocation -> AdminUser.reconstitute(
                7, "operations-admin", "bcrypt-hash", "ENABLED", 1, 1, List.of(), NOW, NOW));
        when(query.count()).thenReturn(1L);
        when(query.page(0, 20)).thenReturn(List.of(new AdminUserQuery.Summary(7, "operations-admin", "ENABLED", 1, 1)));
        var service = new AdminUserApplicationService(admins, query, passwords, audit, CLOCK);

        var created = service.create(1, "operations-admin", "StrongPassword1");
        assertThat(created.username()).isEqualTo("operations-admin");
        assertThat(created.getClass().getRecordComponents()).extracting("name").doesNotContain("password", "passwordHash");
        assertThat(service.page(1, 20).items()).hasSize(1);

        var lastSuperAdmin = admin(7, "ENABLED", 1, 1);
        when(admins.findById(7)).thenReturn(Optional.of(lastSuperAdmin));
        when(admins.isSuperAdmin(7)).thenReturn(true);
        when(admins.countEnabledSuperAdmins()).thenReturn(1L);
        assertThatThrownBy(() -> service.changeStatus(1, 7, AdminUserStatus.DISABLED))
                .isInstanceOf(UseCaseException.class)
                .extracting(error -> ((UseCaseException) error).kind())
                .isEqualTo(UseCaseException.Kind.CONFLICT);
        verify(admins, never()).save(lastSuperAdmin);
        verify(audit).record(1, "ADMIN_DISABLE", "ADMIN", "7", "FAILURE", "LAST_SUPER_ADMIN");
    }

    @Test
    @DisplayName("STORY-001-02-01-02/TC-001..003 role lifecycle, disabled status, and built-in protection")
    void roleLifecycle() {
        var roles = mock(RoleRepository.class);
        var audit = mock(AuditRecorder.class);
        when(roles.add(any())).thenReturn(Role.reconstitute(2, "OPS_ADMIN", "Operations", null,
                "ENABLED", false, List.of(), List.of()));
        when(roles.findAll()).thenReturn(List.of(Role.reconstitute(2, "OPS_ADMIN", "Operations", null,
                "DISABLED", false, List.of(), List.of())));
        var service = rbac(mock(AdminUserRepository.class), roles, mock(PermissionRepository.class),
                mock(MenuRepository.class), audit);

        service.createRole(1, "OPS_ADMIN", "Operations", null);
        assertThat(service.listRoles()).singleElement().extracting(RbacAdministrationApplicationService.RoleView::status)
                .isEqualTo("DISABLED");
        when(roles.add(any())).thenThrow(new DuplicateResourceException("duplicate", null));
        assertThatThrownBy(() -> service.createRole(1, "OPS_ADMIN", "Duplicate", null))
                .isInstanceOf(UseCaseException.class);

        var builtIn = Role.reconstitute(1, "SUPER_ADMIN", "Super", null,
                "ENABLED", true, List.of(1L), List.of(1L));
        when(roles.findById(1)).thenReturn(Optional.of(builtIn));
        assertThatThrownBy(() -> service.updateRole(1, 1, "Changed", null, "DISABLED"))
                .isInstanceOf(UseCaseException.class);
        assertThatThrownBy(() -> service.deleteRole(1, 1)).isInstanceOf(UseCaseException.class);
    }

    @Test
    @DisplayName("STORY-001-02-01-03/TC-001..003 atomic administrator role replacement")
    void administratorRoleReplacement() {
        var admins = mock(AdminUserRepository.class);
        var roles = mock(RoleRepository.class);
        var admin = admin(7, "ENABLED", 1, 4);
        when(admins.findById(7)).thenReturn(Optional.of(admin));
        when(roles.allEnabled(anyCollection())).thenReturn(true);
        var service = rbac(admins, roles, mock(PermissionRepository.class), mock(MenuRepository.class), mock(AuditRecorder.class));

        service.replaceAdminRoles(1, 7, List.of(2L, 3L));
        assertThat(admin.roleIds()).containsExactlyInAnyOrder(2L, 3L);
        assertThat(admin.permissionVersion()).isEqualTo(5);
        verify(admins).replaceRoles(admin);

        when(roles.allEnabled(anyCollection())).thenReturn(false);
        assertThatThrownBy(() -> service.replaceAdminRoles(1, 7, List.of(9L)))
                .isInstanceOf(UseCaseException.class);
        assertThat(admin.roleIds()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("STORY-001-02-02-01/TC-001..003 menu hierarchy, cycle, and type validation")
    void menuRules() {
        var menus = mock(MenuRepository.class);
        when(menus.add(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(menus.findAll()).thenReturn(List.of(
                Menu.reconstitute(2, 1L, "Users", "PAGE", "/users", "Users", null, 2, true, "ENABLED"),
                Menu.reconstitute(1, null, "Security", "DIRECTORY", null, null, null, 1, true, "ENABLED")));
        var service = rbac(mock(AdminUserRepository.class), mock(RoleRepository.class),
                mock(PermissionRepository.class), menus, mock(AuditRecorder.class));

        service.createMenu(1, null, "Security", "DIRECTORY", null, null, null, 1, true);
        assertThat(service.listMenus()).extracting(RbacAdministrationApplicationService.MenuView::id)
                .containsExactly(2L, 1L);
        assertThatThrownBy(() -> service.createMenu(1, null, "Invalid", "PAGE", null, null, null, 0, true))
                .isInstanceOf(UseCaseException.class);

        var current = Menu.reconstitute(1, null, "Security", "DIRECTORY", null, null, null, 1, true, "ENABLED");
        when(menus.findById(1)).thenReturn(Optional.of(current));
        when(menus.findById(2)).thenReturn(Optional.of(Menu.reconstitute(
                2, 1L, "Users", "PAGE", "/users", "Users", null, 2, true, "ENABLED")));
        when(menus.isDescendant(1, 2)).thenReturn(true);
        assertThatThrownBy(() -> service.updateMenu(1, 1, 2L, "Security", "DIRECTORY",
                null, null, null, 1, true, "ENABLED")).isInstanceOf(UseCaseException.class);

        when(menus.findById(3)).thenReturn(Optional.of(Menu.reconstitute(
                3, null, "Action", "ACTION", null, null, "admin:update", 3, true, "ENABLED")));
        assertThatThrownBy(() -> service.createMenu(1, 3L, "Child", "PAGE", "/child", "Users", null, 1, true))
                .isInstanceOf(UseCaseException.class);
    }

    @Test
    @DisplayName("STORY-001-02-02-02/TC-001..003 permission stable code and status lifecycle")
    void permissionRules() {
        var permissions = mock(PermissionRepository.class);
        var service = rbac(mock(AdminUserRepository.class), mock(RoleRepository.class), permissions,
                mock(MenuRepository.class), mock(AuditRecorder.class));
        when(permissions.add(any())).thenReturn(Permission.reconstitute(
                4, "admin:read", "Read admin", null, "API", "ENABLED", "/api/admin/**", "get"));
        when(permissions.findAll()).thenReturn(List.of(Permission.reconstitute(
                4, "admin:read", "Read admin", null, "API", "DISABLED", "/api/admin/**", "GET")));

        service.createPermission(1, "admin:read", "Read admin", null, "API", "/api/admin/**", "GET");
        assertThat(service.listPermissions()).singleElement().satisfies(view -> {
            assertThat(view.code()).isEqualTo("admin:read");
            assertThat(view.status()).isEqualTo("DISABLED");
        });
        assertThatThrownBy(() -> service.createPermission(1, "INVALID", "Bad", null, "BUTTON", null, null))
                .isInstanceOf(UseCaseException.class);

        var permission = Permission.reconstitute(4, "admin:read", "Read admin", null,
                "API", "ENABLED", "/api/admin/**", "GET");
        when(permissions.findById(4)).thenReturn(Optional.of(permission));
        service.updatePermission(1, 4, "Read administrators", "stable code", "DISABLED", "/api/admin/**", "GET");
        assertThat(permission.code()).isEqualTo("admin:read");
        assertThat(permission.status()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("STORY-001-02-02-03/TC-001..003 atomic role authorization replacement")
    void roleAuthorizationReplacement() {
        var roles = mock(RoleRepository.class);
        var permissions = mock(PermissionRepository.class);
        var menus = mock(MenuRepository.class);
        var role = Role.reconstitute(2, "OPS_ADMIN", "Operations", null,
                "ENABLED", false, List.of(8L), List.of(9L));
        when(roles.findById(2)).thenReturn(Optional.of(role));
        when(permissions.allEnabled(anyCollection())).thenReturn(true);
        when(menus.allEnabled(anyCollection())).thenReturn(true);
        var service = rbac(mock(AdminUserRepository.class), roles, permissions, menus, mock(AuditRecorder.class));

        service.replaceRoleAuthorizations(1, 2, List.of(4L), List.of(5L));
        assertThat(role.permissionIds()).containsExactly(4L);
        assertThat(role.menuIds()).containsExactly(5L);
        verify(roles).saveAuthorizations(role);
        verify(roles).bumpMembersVersion(2);

        when(permissions.allEnabled(anyCollection())).thenReturn(false);
        assertThatThrownBy(() -> service.replaceRoleAuthorizations(1, 2, List.of(99L), List.of(5L)))
                .isInstanceOf(UseCaseException.class);
        assertThat(role.permissionIds()).containsExactly(4L);
    }

    @Test
    @DisplayName("STORY-001-02-03-01/TC-001..003 default-deny authorization decision")
    void authorizationDecision() {
        var admins = mock(AdminUserRepository.class);
        var authorization = mock(AuthorizationQueryService.class);
        when(admins.findById(7)).thenReturn(Optional.of(admin(7, "ENABLED", 1, 3)));
        when(authorization.get(7, 3)).thenReturn(new AuthorizationSnapshot(7, 3, Set.of("admin:read"), List.of()));
        var decisions = new AuthorizationDecisionService(admins, authorization);

        assertThat(decisions.permits(7, "admin:read")).isTrue();
        assertThat(decisions.permits(7, "admin:write")).isFalse();
        assertThat(decisions.permits(404, "admin:read")).isFalse();
        when(admins.findById(8)).thenReturn(Optional.of(admin(8, "DISABLED", 1, 3)));
        assertThat(decisions.permits(8, "admin:read")).isFalse();
    }

    @Test
    @DisplayName("STORY-001-02-03-03/TC-001..003 success/failure audit and append-only interface")
    void auditCoverage() {
        var roles = mock(RoleRepository.class);
        var audit = mock(AuditRecorder.class);
        when(roles.add(any())).thenReturn(Role.reconstitute(
                2, "OPS_ADMIN", "Operations", null, "ENABLED", false, List.of(), List.of()));
        when(roles.findById(1)).thenReturn(Optional.of(Role.reconstitute(
                1, "SUPER_ADMIN", "Super", null, "ENABLED", true, List.of(1L), List.of(1L))));
        var service = rbac(mock(AdminUserRepository.class), roles, mock(PermissionRepository.class),
                mock(MenuRepository.class), audit);

        service.createRole(9, "OPS_ADMIN", "Operations", null);
        verify(audit).record(9, "ROLE_CREATE", "ROLE", "OPS_ADMIN", "SUCCESS", null);
        assertThatThrownBy(() -> service.deleteRole(9, 1)).isInstanceOf(UseCaseException.class);
        verify(audit).record(9, "ROLE_DELETE", "ROLE", "1", "FAILURE", "BUILT_IN_ROLE");
        assertThat(AuditRecorder.class.getDeclaredMethods()).extracting("name").containsExactly("record");
    }

    @Test
    @DisplayName("STORY-001-03-01-01/TC-001 administrator bootstrap identity and disabled rejection")
    void administratorBootstrap() {
        var admins = mock(AdminUserRepository.class);
        var authorization = mock(AuthorizationQueryService.class);
        when(admins.findById(7)).thenReturn(Optional.of(admin(7, "ENABLED", 1, 3)));
        when(authorization.get(7, 3)).thenReturn(new AuthorizationSnapshot(7, 3, Set.of("admin:read"), List.of()));
        var service = new AdminSessionQueryService(admins, authorization);

        var bootstrap = service.bootstrap(7);
        assertThat(bootstrap.user().id()).isEqualTo("7");
        assertThat(bootstrap.user().username()).isEqualTo("admin-7");
        assertThat(bootstrap.permissionVersion()).isEqualTo(3);

        when(admins.findById(8)).thenReturn(Optional.of(admin(8, "DISABLED", 1, 3)));
        assertThatThrownBy(() -> service.bootstrap(8)).isInstanceOf(UseCaseException.class)
                .extracting(error -> ((UseCaseException) error).kind())
                .isEqualTo(UseCaseException.Kind.UNAUTHORIZED);
    }

    @Test
    @DisplayName("STORY-001-03-01-02/TC-001 versioned permission and menu snapshot")
    void authorizationSnapshot() {
        var menu = new MenuNode(2, null, "Users", "PAGE", "/users", "Users",
                "admin:read", 1, true, List.of());
        AuthorizationRepository repository = (adminId, version) ->
                new AuthorizationSnapshot(adminId, version, Set.of("admin:read"), List.of(menu));
        AuthorizationCache cache = mock(AuthorizationCache.class);
        when(cache.get(7, 3)).thenReturn(Optional.empty());
        var snapshot = new AuthorizationQueryService(repository, cache).get(7, 3);

        assertThat(snapshot.permissions()).containsExactly("admin:read");
        assertThat(snapshot.permits("admin:read")).isTrue();
        assertThat(snapshot.menus()).singleElement().extracting(MenuNode::path).isEqualTo("/users");
        verify(cache).put(snapshot);
    }

    @Test
    @DisplayName("STORY-001-01-02-03/TC-001..003 logout revocation is idempotent and stale sessions fail")
    void logoutAndStaleSession() {
        var repository = new MemoryRefreshRepository();
        var service = new RefreshSessionApplicationService(repository, CLOCK, new SecureRandom(), Duration.ofDays(7));
        var issued = service.issue(7, 2);
        service.revokeAll(7);
        service.revokeAll(7);
        assertThatThrownBy(() -> service.rotate(issued.value(), 2))
                .isInstanceOf(RefreshSessionApplicationService.InvalidRefreshTokenException.class);
        assertThat(repository.tokens.get(RefreshSessionApplicationService.digest(issued.value())).revokedAt()).isEqualTo(NOW);
    }

    private static RbacAdministrationApplicationService rbac(
            AdminUserRepository admins,
            RoleRepository roles,
            PermissionRepository permissions,
            MenuRepository menus,
            AuditRecorder audit) {
        return new RbacAdministrationApplicationService(admins, roles, permissions, menus, audit);
    }

    private static AdminUser admin(long id, String status, long authVersion, long permissionVersion) {
        return AdminUser.reconstitute(id, "admin-" + id, "bcrypt-hash", status,
                authVersion, permissionVersion, List.of(), NOW, NOW);
    }

    private static final class MemoryRefreshRepository implements RefreshSessionRepository {
        private final Map<String, RefreshSession> tokens = new HashMap<>();

        @Override
        public Optional<RefreshSession> findByDigest(String digest) {
            return Optional.ofNullable(tokens.get(digest));
        }

        @Override
        public void save(RefreshSession token) {
            tokens.put(token.digest(), token);
        }

        @Override
        public boolean consume(String digest, long authVersion, Instant at) {
            var token = tokens.get(digest);
            if (token == null || token.authVersion() != authVersion || !token.isActive(at)) return false;
            tokens.put(digest, token.markUsed(at));
            return true;
        }

        @Override
        public void revokeFamily(String familyId, Instant at) {
            tokens.replaceAll((digest, token) -> token.familyId().equals(familyId) ? token.revoke(at) : token);
        }

        @Override
        public void revokeAllForAdmin(long adminId, Instant at) {
            tokens.replaceAll((digest, token) -> token.adminId() == adminId ? token.revoke(at) : token);
        }
    }
}
