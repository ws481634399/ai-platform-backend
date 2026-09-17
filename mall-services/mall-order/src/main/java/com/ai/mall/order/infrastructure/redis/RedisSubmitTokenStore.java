package com.ai.mall.order.infrastructure.redis;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.port.SubmitTokenStore;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 下单令牌 Redis 存储（CHG-0019）。
 *
 * <p>键：{@code order:submit-token:{memberId}:{token}}，TTL 600s。
 * 消费用 Lua 脚本原子 GET+DEL（单脚本天然原子，兼容 Redis 各版本），
 * 保证双击/重放下只有一个请求能取到载荷。Redis 故障归一为 503。
 */
@Component
public class RedisSubmitTokenStore implements SubmitTokenStore {

    private static final String KEY_PREFIX = "order:submit-token:";

    /** 原子取出并删除：返回旧值或 nil。 */
    private static final DefaultRedisScript<String> GET_DEL = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]); if v then redis.call('DEL', KEYS[1]) end; return v",
            String.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSubmitTokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void issue(long memberId, String token, Payload payload, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(memberId, token), objectMapper.writeValueAsString(payload), ttl);
        } catch (Exception ex) {
            throw new BusinessException(OrderErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public Optional<Payload> consume(long memberId, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String raw;
        try {
            raw = redisTemplate.execute(GET_DEL, List.of(key(memberId, token)));
        } catch (RuntimeException ex) {
            throw new BusinessException(OrderErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            PayloadDto dto = objectMapper.readValue(raw, PayloadDto.class);
            List<Payload.Line> lines = dto.items() == null ? List.of()
                    : dto.items().stream()
                            .map(line -> new Payload.Line(Long.parseLong(line.skuId()), line.quantity()))
                            .toList();
            Long addressId = dto.addressId() == null ? null : Long.parseLong(dto.addressId());
            return Optional.of(new Payload(dto.source(), addressId, lines));
        } catch (Exception ex) {
            // 载荷损坏：令牌已被 GETDEL 消费，按失效处理（引导重新预览）
            return Optional.empty();
        }
    }

    private static String key(long memberId, String token) {
        return KEY_PREFIX + memberId + ":" + token;
    }

    /** 令牌载荷线框：ID 以字符串落 JSON，避免大整数歧义（此处数值均在安全范围内，统一可读性）。 */
    record PayloadDto(String source, String addressId, List<LineDto> items) {
    }

    record LineDto(String skuId, int quantity) {
    }
}
