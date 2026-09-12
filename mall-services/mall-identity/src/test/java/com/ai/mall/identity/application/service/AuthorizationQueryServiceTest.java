package com.ai.mall.identity.application.service;

import com.ai.mall.identity.application.dto.AuthorizationSnapshot;
import com.ai.mall.identity.application.port.AuthorizationCache;
import com.ai.mall.identity.application.port.AuthorizationRepository;
import com.ai.mall.identity.infrastructure.cache.InMemoryAuthorizationCache;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import java.util.Optional;

class AuthorizationQueryServiceTest {
    @Test void cachesByAdminAndPermissionVersion() {
        var loads = new AtomicInteger();
        AuthorizationRepository repository = (id, version) -> {
            loads.incrementAndGet();
            return new AuthorizationSnapshot(id, version, Set.of("admin:read"), List.of());
        };
        var service = new AuthorizationQueryService(repository, new InMemoryAuthorizationCache());
        assertThat(service.get(1, 2).permits("admin:read")).isTrue();
        service.get(1, 2);
        service.get(1, 3);
        assertThat(loads).hasValue(2);
    }

    @Test void fallsBackToDatabaseWhenCacheFailsButNeverMasksDatabaseFailure() {
        AuthorizationCache broken = new AuthorizationCache() {
            public Optional<AuthorizationSnapshot> get(long id, long version) { throw new IllegalStateException("redis down"); }
            public void put(AuthorizationSnapshot snapshot) { throw new IllegalStateException("redis down"); }
        };
        var snapshot = new AuthorizationQueryService((id, version) -> new AuthorizationSnapshot(id, version, Set.of("x:read"), List.of()), broken).get(7, 3);
        assertThat(snapshot.permits("x:read")).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AuthorizationQueryService((id, version) -> { throw new IllegalStateException("db down"); }, broken).get(7, 3))
                .hasMessage("db down");
    }
}
