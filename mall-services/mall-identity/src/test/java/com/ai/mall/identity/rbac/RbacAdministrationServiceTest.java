package com.ai.mall.identity.rbac;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ai.mall.identity.persistence.RbacCommandMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RbacAdministrationServiceTest {
    @Test void invalidRoleSetIsRejectedBeforeExistingAssignmentsAreCleared() {
        var mapper = mock(RbacCommandMapper.class);
        when(mapper.countEnabledRoles(List.of(2L, 3L))).thenReturn(1);
        var service = new RbacAdministrationService(mapper);
        assertThatThrownBy(() -> service.replaceAdminRoles(9, 1, List.of(2L, 3L))).hasMessageContaining("role set");
        verify(mapper, never()).clearAdminRoles(anyLong());
    }

    @Test void authorizationReplacementUpdatesBothSetsAndInvalidatesMembers() {
        var mapper = mock(RbacCommandMapper.class);
        when(mapper.countEnabledPermissions(anyCollection())).thenReturn(1);
        when(mapper.countEnabledMenus(anyCollection())).thenReturn(1);
        when(mapper.countEnabledRoles(anyCollection())).thenReturn(1);
        new RbacAdministrationService(mapper).replaceRoleAuthorizations(9, 2, List.of(4L), List.of(5L));
        verify(mapper).clearRolePermissions(2); verify(mapper).clearRoleMenus(2);
        verify(mapper).addRolePermissions(eq(2L), argThat(ids -> ids.equals(new java.util.LinkedHashSet<>(List.of(4L)))));
        verify(mapper).addRoleMenus(eq(2L), argThat(ids -> ids.equals(new java.util.LinkedHashSet<>(List.of(5L)))));
        verify(mapper).bumpRoleMembersPermissionVersion(2);
    }

    @Test void builtInRoleCannotBeChangedOrDeleted() {
        var mapper = mock(RbacCommandMapper.class);
        when(mapper.findRole(1)).thenReturn(new RbacCommandMapper.RoleRow(1, "SUPER_ADMIN", "Super", null, "ENABLED", true));
        var service = new RbacAdministrationService(mapper);
        assertThatThrownBy(() -> service.updateRole(9, 1, "x", null, "DISABLED")).hasMessageContaining("built-in");
        assertThatThrownBy(() -> service.deleteRole(9, 1)).hasMessageContaining("built-in");
        verify(mapper, never()).updateRole(anyLong(), anyString(), any(), anyString());
        verify(mapper, never()).deleteRole(anyLong());
    }
}
