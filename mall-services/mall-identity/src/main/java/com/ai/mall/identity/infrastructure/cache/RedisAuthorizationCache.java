package com.ai.mall.identity.infrastructure.cache;

import com.ai.mall.common.security.AuthorizationKeys;
import com.ai.mall.identity.application.dto.AuthorizationSnapshot;
import com.ai.mall.identity.application.port.AuthorizationCache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisAuthorizationCache implements AuthorizationCache {

    /** 快照与指针同 TTL：过期后下游 401，由下次会话引导重新写入 */
    private static final Duration TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisAuthorizationCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AuthorizationSnapshot> get(long adminId, long version) {
        String json = redis.opsForValue().get(InMemoryAuthorizationCache.key(adminId, version));
        if (json == null) return Optional.empty();
        try { return Optional.of(objectMapper.readValue(json, AuthorizationSnapshot.class)); }
        catch (JsonProcessingException e) { redis.delete(InMemoryAuthorizationCache.key(adminId, version)); return Optional.empty(); }
    }

    @Override
    public void put(AuthorizationSnapshot snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            // 版本化快照 + 当前版本指针（下游 JWT 不含 permissionVersion，经指针定位最新快照）
            redis.opsForValue().set(AuthorizationKeys.snapshot(snapshot.adminId(), snapshot.permissionVersion()),
                    json, TTL);
            redis.opsForValue().set(AuthorizationKeys.current(snapshot.adminId()),
                    String.valueOf(snapshot.permissionVersion()), TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize authorization snapshot", e);
        }
    }
}
