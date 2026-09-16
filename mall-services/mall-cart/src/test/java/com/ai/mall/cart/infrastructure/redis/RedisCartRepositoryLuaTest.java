package com.ai.mall.cart.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.mall.cart.domain.cart.CartConstants;
import com.ai.mall.cart.domain.cart.CartItem;
import com.ai.mall.cart.domain.cart.CartRepository.MergeItem;
import com.ai.mall.cart.domain.cart.CartRepository.MergeResult;
import com.ai.mall.cart.domain.cart.CartScriptCode;
import com.ai.mall.cart.support.AbstractRedisIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 购物车 Lua 脚本真实 Redis 集成测试（CHG-0018 DU-BE-801）。
 * 覆盖 AC-001/002/004/005/006 的原子写语义与 90 天滑动 TTL。
 */
class RedisCartRepositoryLuaTest extends AbstractRedisIntegrationTest {

    private static final long MEMBER = 5001L;
    private static final String KEY = CartConstants.memberKey(MEMBER);

    private StringRedisTemplate redisTemplate;
    private RedisCartRepository repository;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.delete(KEY);
        repository = new RedisCartRepository(redisTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("AC-001 新加购：默认勾选、写入快照价、写后 TTL≈7776000")
    void addNewItemSetsDefaultsAndTtl() {
        CartScriptCode code = repository.add(MEMBER, 1001L, 2, 399900L);

        assertThat(code).isEqualTo(CartScriptCode.OK);
        List<CartItem> items = repository.findItems(MEMBER);
        assertThat(items).hasSize(1);
        CartItem item = items.get(0);
        assertThat(item.skuId()).isEqualTo(1001L);
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.selected()).isTrue();
        assertThat(item.priceFenAtAdded()).isEqualTo(399900L);
        assertThat(item.createdAt()).isNotBlank();
        assertThat(item.updatedAt()).isEqualTo(item.createdAt());

        Long ttl = redisTemplate.getExpire(KEY);
        assertThat(ttl).isNotNull().isBetween(CartConstants.TTL_SECONDS - 60, CartConstants.TTL_SECONDS);
    }

    @Test
    @DisplayName("AC-002 同 SKU 合并为一条，数量累加")
    void addMergesSameSku() {
        repository.add(MEMBER, 1001L, 1, 100L);
        CartScriptCode code = repository.add(MEMBER, 1001L, 4, 100L);

        assertThat(code).isEqualTo(CartScriptCode.OK);
        List<CartItem> items = repository.findItems(MEMBER);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).quantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("AC-002 合并后 >999 拒绝且保持原数量（600+400；999+1 同路径）")
    void addOverQuantityLimitKeepsOriginal() {
        repository.add(MEMBER, 1001L, 600, 100L);
        assertThat(repository.add(MEMBER, 1001L, 400, 100L)).isEqualTo(CartScriptCode.QTY_LIMIT);
        assertThat(repository.findItems(MEMBER).get(0).quantity()).isEqualTo(600);

        repository.add(MEMBER, 1002L, 999, 100L);
        assertThat(repository.add(MEMBER, 1002L, 1, 100L)).isEqualTo(CartScriptCode.QTY_LIMIT);
        assertThat(repository.findItems(MEMBER).stream().filter(i -> i.skuId() == 1002L).findFirst()
                .orElseThrow().quantity()).isEqualTo(999);
    }

    @Test
    @DisplayName("AC-004 第 101 个不同 SKU 拒绝，车保持 100 条")
    void addRejects101stDistinctSku() {
        for (long skuId = 1; skuId <= CartConstants.MAX_ITEMS; skuId++) {
            assertThat(repository.add(MEMBER, skuId, 1, 100L)).isEqualTo(CartScriptCode.OK);
        }
        assertThat(repository.add(MEMBER, 9999L, 1, 100L)).isEqualTo(CartScriptCode.ITEMS_LIMIT);

        Long size = redisTemplate.opsForHash().size(KEY);
        assertThat(size).isEqualTo(CartConstants.MAX_ITEMS);
        assertThat(repository.findItems(MEMBER)).noneMatch(i -> i.skuId() == 9999L);
    }

    @Test
    @DisplayName("AC-005 改量：不存在 NOT_FOUND；存在更新；非法量脚本兜底")
    void updateQuantityLifecycle() {
        assertThat(repository.updateQuantity(MEMBER, 404L, 3)).isEqualTo(CartScriptCode.NOT_FOUND);

        repository.add(MEMBER, 1001L, 1, 100L);
        assertThat(repository.updateQuantity(MEMBER, 1001L, 999)).isEqualTo(CartScriptCode.OK);
        assertThat(repository.findItems(MEMBER).get(0).quantity()).isEqualTo(999);
        assertThat(repository.updateQuantity(MEMBER, 1001L, 1000)).isEqualTo(CartScriptCode.QTY_LIMIT);
        assertThat(repository.updateQuantity(MEMBER, 1001L, 0)).isEqualTo(CartScriptCode.QTY_LIMIT);
    }

    @Test
    @DisplayName("AC-005 单删/批删幂等，不存在项忽略，空车不重建 key TTL")
    void removeIsIdempotent() {
        repository.add(MEMBER, 1001L, 1, 100L);
        repository.add(MEMBER, 1002L, 1, 100L);
        repository.add(MEMBER, 1003L, 1, 100L);

        repository.remove(MEMBER, 1001L);
        repository.remove(MEMBER, 1001L);
        repository.removeBatch(MEMBER, List.of(1002L, 404L));
        assertThat(repository.findItems(MEMBER)).extracting(CartItem::skuId).containsExactly(1003L);

        repository.removeBatch(MEMBER, List.of(1003L, 1003L));
        assertThat(redisTemplate.hasKey(KEY)).isFalse();
        // 对不存在的车删除仍幂等成功，不重建 key
        repository.remove(MEMBER, 8888L);
        assertThat(redisTemplate.hasKey(KEY)).isFalse();
    }

    @Test
    @DisplayName("AC-006 单条目勾选/取消：不存在 NOT_FOUND；全车全选/取消生效")
    void selectSemantics() {
        repository.add(MEMBER, 1001L, 1, 100L);
        repository.add(MEMBER, 1002L, 1, 100L);
        assertThat(repository.selectOne(MEMBER, 404L, true)).isEqualTo(CartScriptCode.NOT_FOUND);

        assertThat(repository.selectOne(MEMBER, 1001L, false)).isEqualTo(CartScriptCode.OK);
        assertThat(repository.findItems(MEMBER)).filteredOn(CartItem::selected).extracting(CartItem::skuId)
                .containsExactly(1002L);

        repository.selectAll(MEMBER, false);
        assertThat(repository.findItems(MEMBER)).noneMatch(CartItem::selected);
        repository.selectAll(MEMBER, true);
        assertThat(repository.findItems(MEMBER)).allMatch(CartItem::selected);

        // 空车全选为 no-op 成功
        redisTemplate.delete(KEY);
        repository.selectAll(MEMBER, true);
        assertThat(repository.findItems(MEMBER)).isEmpty();
    }

    @Test
    @DisplayName("写操作滑动续期：删除最后一条后 key 消失；其他写后 TTL 重置")
    void writesRenewTtl() throws InterruptedException {
        repository.add(MEMBER, 1001L, 1, 100L);
        // 模拟旧 TTL 剩余 60 秒后再写
        redisTemplate.expire(KEY, java.time.Duration.ofSeconds(60));
        Thread.sleep(1100);
        repository.add(MEMBER, 1002L, 1, 100L);
        Long ttl = redisTemplate.getExpire(KEY);
        assertThat(ttl).isGreaterThan(CartConstants.TTL_SECONDS - 60);
    }

    @Test
    @DisplayName("会员车物理隔离：不同 memberId key 互不可见")
    void memberCartsAreIsolated() {
        long other = 5002L;
        redisTemplate.delete(CartConstants.memberKey(other));
        repository.add(MEMBER, 1001L, 1, 100L);
        repository.add(other, 2002L, 2, 200L);

        assertThat(repository.findItems(MEMBER)).extracting(CartItem::skuId).containsExactly(1001L);
        assertThat(repository.findItems(other)).extracting(CartItem::skuId).containsExactly(2002L);
    }

    // ---------- CHG-0018 DU-BE-803 合并 Lua ----------

    @Test
    @DisplayName("AC-018 合并 token 不存在 → TOKEN_MISSING")
    void mergeTokenMissing() {
        MergeResult result = repository.merge(MEMBER, "nonexistent", List.of());
        assertThat(result.dropped()).hasSize(1);
        assertThat(result.dropped().get(0).reason()).isEqualTo("TOKEN_MISSING");
    }

    @Test
    @DisplayName("AC-018 合并 token 值≠memberId → TOKEN_MISMATCH")
    void mergeTokenMismatch() {
        String token = repository.issueMergeToken(MEMBER);
        // 用另一个 memberId 消费
        MergeResult result = repository.merge(MEMBER + 1, token, List.of());
        assertThat(result.dropped()).hasSize(1);
        assertThat(result.dropped().get(0).reason()).isEqualTo("TOKEN_MISMATCH");
    }

    @Test
    @DisplayName("AC-016 同 SKU 数量相加；异 SKU 并入")
    void mergeSameSkuAccumulatesAndDifferentSkuAdded() {
        repository.add(MEMBER, 1001L, 1, 100L);
        String token = repository.issueMergeToken(MEMBER);

        MergeResult result = repository.merge(MEMBER, token, List.of(
                new MergeItem(1001L, 2, true, 100L),
                new MergeItem(1002L, 3, false, 200L)));

        assertThat(result.merged()).hasSize(2);
        assertThat(result.truncated()).isEmpty();
        assertThat(result.dropped()).isEmpty();

        List<CartItem> items = repository.findItems(MEMBER);
        assertThat(items).hasSize(2);
        CartItem same = items.stream().filter(i -> i.skuId() == 1001L).findFirst().orElseThrow();
        assertThat(same.quantity()).isEqualTo(3);
        assertThat(same.selected()).isTrue();
        CartItem diff = items.stream().filter(i -> i.skuId() == 1002L).findFirst().orElseThrow();
        assertThat(diff.quantity()).isEqualTo(3);
        assertThat(diff.selected()).isFalse();
    }

    @Test
    @DisplayName("AC-017 合并后超 999 截断并返回 truncated.finalQuantity")
    void mergeTruncatesOver999() {
        repository.add(MEMBER, 1001L, 998, 100L);
        String token = repository.issueMergeToken(MEMBER);

        MergeResult result = repository.merge(MEMBER, token, List.of(new MergeItem(1001L, 5, true, 100L)));

        assertThat(result.truncated()).hasSize(1);
        assertThat(result.truncated().get(0).skuId()).isEqualTo(1001L);
        assertThat(result.truncated().get(0).finalQuantity()).isEqualTo(999);
        assertThat(repository.findItems(MEMBER).get(0).quantity()).isEqualTo(999);
    }

    @Test
    @DisplayName("AC-017 超 100 条目新 SKU dropped（CART_ITEMS_LIMIT），会员条目保留")
    void mergeDropsOver100Items() {
        for (long sku = 1; sku <= CartConstants.MAX_ITEMS; sku++) {
            repository.add(MEMBER, sku, 1, 100L);
        }
        String token = repository.issueMergeToken(MEMBER);

        MergeResult result = repository.merge(MEMBER, token, List.of(new MergeItem(9999L, 1, true, 100L)));

        assertThat(result.dropped()).hasSize(1);
        assertThat(result.dropped().get(0).skuId()).isEqualTo(9999L);
        assertThat(result.dropped().get(0).reason()).isEqualTo("CART_ITEMS_LIMIT");
        assertThat(redisTemplate.opsForHash().size(KEY)).isEqualTo(CartConstants.MAX_ITEMS);
    }

    @Test
    @DisplayName("AC-018 同 token 二次调用不累加（幂等：第二次 TOKEN_MISSING）")
    void mergeIsIdempotent() {
        repository.add(MEMBER, 1001L, 1, 100L);
        String token = repository.issueMergeToken(MEMBER);

        MergeResult first = repository.merge(MEMBER, token, List.of(new MergeItem(1001L, 2, true, 100L)));
        assertThat(first.merged()).hasSize(1);
        assertThat(repository.findItems(MEMBER).get(0).quantity()).isEqualTo(3);

        // 重放同一 token
        MergeResult second = repository.merge(MEMBER, token, List.of(new MergeItem(1001L, 2, true, 100L)));
        assertThat(second.dropped()).hasSize(1);
        assertThat(second.dropped().get(0).reason()).isEqualTo("TOKEN_MISSING");
        // 数量不变，未二次累加
        assertThat(repository.findItems(MEMBER).get(0).quantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-018 合并后续期 90 天 TTL；token TTL≈300 秒")
    void mergeRenewsCartTtlAndTokenTtl() throws InterruptedException {
        repository.add(MEMBER, 1001L, 1, 100L);
        redisTemplate.expire(KEY, java.time.Duration.ofSeconds(60));
        Thread.sleep(1100);

        String token = repository.issueMergeToken(MEMBER);
        Long tokenTtl = redisTemplate.getExpire(CartConstants.mergeTokenKey(token));
        assertThat(tokenTtl).isNotNull().isBetween(290L, 300L);

        repository.merge(MEMBER, token, List.of(new MergeItem(1002L, 1, true, 200L)));
        Long cartTtl = redisTemplate.getExpire(KEY);
        assertThat(cartTtl).isGreaterThan(CartConstants.TTL_SECONDS - 60);
        // token 已被消费删除
        assertThat(redisTemplate.hasKey(CartConstants.mergeTokenKey(token))).isFalse();
    }
}
