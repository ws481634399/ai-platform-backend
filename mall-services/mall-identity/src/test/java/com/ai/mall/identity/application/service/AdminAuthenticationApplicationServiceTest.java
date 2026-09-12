package com.ai.mall.identity.application.service;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;import com.ai.mall.identity.application.port.PasswordHasher;import com.ai.mall.identity.domain.model.admin.AdminUser;import com.ai.mall.identity.domain.repository.AdminUserRepository;import java.time.Instant;import java.util.*;import org.junit.jupiter.api.*;
class AdminAuthenticationApplicationServiceTest {
 private AdminUserRepository repo;private PasswordHasher hasher;private AdminAuthenticationApplicationService service;private AdminUser admin;
 @BeforeEach void setUp(){repo=mock(AdminUserRepository.class);hasher=mock(PasswordHasher.class);admin=AdminUser.reconstitute(1,"admin","encoded","ENABLED",2,3,List.of(),Instant.now(),Instant.now());when(repo.findByAccount("admin")).thenReturn(Optional.of(admin));when(hasher.matches("correct-pass","encoded")).thenReturn(true);service=new AdminAuthenticationApplicationService(repo,hasher);}
 @Test void authenticatesEnabledAdmin(){assertThat(service.authenticate(" ADMIN ","correct-pass")).contains(new AdminAuthenticationApplicationService.AuthenticatedAdmin(1,"admin",2,3));}
 @Test void usesUniformEmptyResultForUnknownWrongOrDisabled(){assertThat(service.authenticate("missing","correct-pass")).isEmpty();assertThat(service.authenticate("admin","wrong-pass")).isEmpty();admin.changeStatus(com.ai.mall.identity.domain.model.admin.AdminUserStatus.DISABLED,Instant.now());assertThat(service.authenticate("admin","correct-pass")).isEmpty();}
}
