package com.ai.mall.identity.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ai.mall.identity.audit.AuthAuditService;
import com.ai.mall.identity.auth.*;
import com.ai.mall.identity.persistence.AdminCredentialMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminUserServiceTest {
    @Test void disablingLastSuperAdminIsRejectedWithoutMutationAndAudited() {
        var repo = mock(AdminCredentialRepository.class); var mapper = mock(AdminCredentialMapper.class);
        var audit = mock(AuthAuditService.class); var encoder = mock(PasswordEncoder.class);
        when(repo.findById(1)).thenReturn(Optional.of(admin(1, AdminStatus.ENABLED)));
        when(mapper.isSuperAdmin(1)).thenReturn(1L); when(mapper.countEnabledSuperAdmins()).thenReturn(1L);
        var service = new AdminUserService(repo, mapper, encoder, audit);
        assertThatThrownBy(() -> service.changeStatus(9, 1, AdminStatus.DISABLED)).hasMessageContaining("last enabled");
        verify(mapper, never()).updateStatus(anyLong(), anyString());
        verify(audit).record(9, "ADMIN_DISABLE", "ADMIN", "1", "FAILURE", "LAST_SUPER_ADMIN");
    }

    @Test void statusAndPasswordChangesInvalidateAuthenticationVersions() {
        var repo = mock(AdminCredentialRepository.class); var mapper = mock(AdminCredentialMapper.class);
        when(repo.findById(2)).thenReturn(Optional.of(admin(2, AdminStatus.ENABLED)));
        when(mapper.updateStatus(2, "DISABLED")).thenReturn(1); when(mapper.updatePassword(eq(2L), anyString())).thenReturn(1);
        var encoder = mock(PasswordEncoder.class); when(encoder.encode(anyString())).thenReturn("bcrypt");
        var service = new AdminUserService(repo, mapper, encoder, mock(AuthAuditService.class));
        service.changeStatus(9, 2, AdminStatus.DISABLED); service.changePassword(9, 2, "StrongPassword1");
        verify(mapper).updateStatus(2, "DISABLED"); verify(mapper).updatePassword(2, "bcrypt");
    }

    private static AdminCredential admin(long id, AdminStatus status) {
        return new AdminCredential(id, "admin", "hash", status, 1, 1, Instant.now(), Instant.now());
    }
}
