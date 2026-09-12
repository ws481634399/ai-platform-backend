package com.ai.mall.identity.infrastructure.cache;

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
            redis.opsForValue().set(InMemoryAuthorizationCache.key(snapshot.adminId(), snapshot.permissionVersion()),
                    objectMapper.writeValueAsString(snapshot), Duration.ofMinutes(15));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize authorization snapshot", e);
        }
    }
}
