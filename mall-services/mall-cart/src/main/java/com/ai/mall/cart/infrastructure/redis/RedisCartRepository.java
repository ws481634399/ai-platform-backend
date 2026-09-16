package com.ai.mall.cart.infrastructure.redis;

import com.ai.mall.cart.domain.cart.CartConstants;
import com.ai.mall.cart.domain.cart.CartItem;
import com.ai.mall.cart.domain.cart.CartRepository;
import com.ai.mall.cart.domain.cart.CartScriptCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;

/**
 * 购物车 Redis 仓储实现（CHG-0018 DU-BE-801）。
 *
 * <p>每会员一个 Hash（{@code cart:member:{memberId}}），所有写均经 Lua 原子完成
 * 数据变更 + {@code EXPIRE} 滑动续期；脚本返回码到 {@link CartScriptCode} 的映射
 * 集中在本层。值为 {@link CartItem} JSON，读写均用 {@link ObjectMapper}，
 * Redis 故障（连接拒绝/超时等 DataAccessException）原样上抛由应用层归一 503。
 */
@Repository
public class RedisCartRepository implements CartRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final DefaultRedisScript<Long> addScript = loadScript("scripts/cart_add.lua");
    private final DefaultRedisScript<Long> updateScript = loadScript("scripts/cart_update.lua");
    private final DefaultRedisScript<Long> removeScript = loadScript("scripts/cart_remove.lua");
    private final DefaultRedisScript<Long> selectScript = loadScript("scripts/cart_select.lua");

    public RedisCartRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public CartScriptCode add(long memberId, long skuId, int quantity, long priceFenAtAdded) {
        Long result = redisTemplate.execute(addScript, List.of(CartConstants.memberKey(memberId)),
                Long.toString(skuId), Integer.toString(quantity), Long.toString(CartConstants.TTL_SECONDS),
                Instant.now().toString(), Long.toString(priceFenAtAdded),
                Integer.toString(CartConstants.MAX_QUANTITY), Integer.toString(CartConstants.MAX_ITEMS));
        return CartScriptCode.of(result == null ? -1 : result);
    }

    @Override
    public CartScriptCode updateQuantity(long memberId, long skuId, int quantity) {
        Long result = redisTemplate.execute(updateScript, List.of(CartConstants.memberKey(memberId)),
                Long.toString(skuId), Integer.toString(quantity), Long.toString(CartConstants.TTL_SECONDS),
                Instant.now().toString(), Integer.toString(CartConstants.MAX_QUANTITY));
        return CartScriptCode.of(result == null ? -1 : result);
    }

    @Override
    public void remove(long memberId, long skuId) {
        removeBatch(memberId, List.of(skuId));
    }

    @Override
    public void removeBatch(long memberId, List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        List<String> args = new ArrayList<>(skuIds.size() + 1);
        args.add(Long.toString(CartConstants.TTL_SECONDS));
        skuIds.forEach(id -> args.add(Long.toString(id)));
        redisTemplate.execute(removeScript, List.of(CartConstants.memberKey(memberId)), args.toArray());
    }

    @Override
    public CartScriptCode selectOne(long memberId, long skuId, boolean selected) {
        Long result = redisTemplate.execute(selectScript, List.of(CartConstants.memberKey(memberId)),
                "SINGLE", Long.toString(skuId), Boolean.toString(selected),
                Instant.now().toString(), Long.toString(CartConstants.TTL_SECONDS));
        return CartScriptCode.of(result == null ? -1 : result);
    }

    @Override
    public void selectAll(long memberId, boolean selected) {
        redisTemplate.execute(selectScript, List.of(CartConstants.memberKey(memberId)),
                "ALL", "-", Boolean.toString(selected),
                Instant.now().toString(), Long.toString(CartConstants.TTL_SECONDS));
    }

    @Override
    public List<CartItem> findItems(long memberId) {
        Map<Object, Object> raw = redisTemplate.opsForHash()
                .entries(CartConstants.memberKey(memberId));
        List<CartItem> items = new ArrayList<>(raw.size());
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            try {
                Map<String, Object> value = objectMapper.readValue(entry.getValue().toString(), MAP_TYPE);
                items.add(new CartItem(
                        Long.parseLong(entry.getKey().toString()),
                        ((Number) value.get("quantity")).intValue(),
                        Boolean.TRUE.equals(value.get("selected")),
                        ((Number) value.get("priceFenAtAdded")).longValue(),
                        (String) value.get("createdAt"),
                        (String) value.get("updatedAt")));
            } catch (Exception ex) {
                throw new IllegalStateException("购物车条目反序列化失败: " + entry.getKey(), ex);
            }
        }
        return items;
    }

    private static DefaultRedisScript<Long> loadScript(String classpath) {
        try {
            String content = StreamUtils.copyToString(new ClassPathResource(classpath).getInputStream(),
                    java.nio.charset.StandardCharsets.UTF_8);
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(content, Long.class);
            return script;
        } catch (Exception ex) {
            throw new IllegalStateException("加载购物车 Lua 脚本失败: " + classpath, ex);
        }
    }
}
