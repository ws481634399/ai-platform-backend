package com.ai.mall.cart.interfaces.rest.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.cart.application.cart.InventoryAvailabilityClient;
import com.ai.mall.cart.application.cart.InventoryAvailabilityClient.SkuAvailability;
import com.ai.mall.cart.application.cart.ProductSkuClient;
import com.ai.mall.cart.application.cart.ProductSkuClient.SkuSnapshot;
import com.ai.mall.cart.domain.cart.CartConstants;
import com.ai.mall.cart.domain.cart.CartErrorCode;
import com.ai.mall.cart.domain.cart.CartRepository;
import com.ai.mall.cart.support.AbstractRedisIntegrationTest;
import com.ai.mall.cart.support.ApiTestSecurityConfig;
import com.ai.mall.common.web.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 购物车读模型全链路测试（CHG-0018 DU-BE-802）：真实 Redis + 测试安全链 +
 * mock product/inventory 双内部契约。覆盖 AC-008~013、只读不改 Redis、空车零调用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(ApiTestSecurityConfig.class)
class CartReadApiTest extends AbstractRedisIntegrationTest {

    private static final String MEMBER = "6001";
    private static final String EMPTY_MEMBER = "6002";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private CartRepository cartRepository;

    @MockitoBean
    private ProductSkuClient productSkuClient;
    @MockitoBean
    private InventoryAvailabilityClient inventoryAvailabilityClient;

    /** skuId → 商品态夹具；键不存在=product 整体故障；值为 null=缺失占位。 */
    private Map<Long, SkuSnapshot> productFixtures = new LinkedHashMap<>();
    private Map<Long, Long> stockFixtures = new LinkedHashMap<>();
    private boolean productDown;
    private boolean inventoryDown;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(redisTemplate.keys("cart:member:*"));
        productFixtures = new LinkedHashMap<>();
        stockFixtures = new LinkedHashMap<>();
        productDown = false;
        inventoryDown = false;

        lenient().when(productSkuClient.findSnapshots(anyList())).thenAnswer(invocation -> {
            if (productDown) {
                throw new BusinessException(CartErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
            }
            List<Long> ids = invocation.getArgument(0);
            return ids.stream().map(id -> productFixtures.containsKey(id)
                    ? productFixtures.get(id) : missing(id)).toList();
        });
        lenient().when(inventoryAvailabilityClient.findAvailability(anyList())).thenAnswer(invocation -> {
            if (inventoryDown) {
                throw new BusinessException(CartErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
            }
            List<Long> ids = invocation.getArgument(0);
            return ids.stream().map(id -> new SkuAvailability(id, stockFixtures.getOrDefault(id, 0L))).toList();
        });
    }

    @Test
    @DisplayName("AC-008/013 读车回填图/名/规格/最新价/双状态与选中合计，ID 字符串、库存精确数不外泄")
    void readModelEnriched() throws Exception {
        cartRepository.add(Long.parseLong(MEMBER), 1001L, 2, 39900L);
        productFixtures.put(1001L, snap(1001, "ON_SALE", "ENABLED", 39900L));
        stockFixtures.put(1001L, 20L);

        expectGetOk()
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].skuId").value("1001"))
                .andExpect(jsonPath("$.data.items[0].productId").value("9001"))
                .andExpect(jsonPath("$.data.items[0].productName").value("演示手机"))
                .andExpect(jsonPath("$.data.items[0].skuName").value("SKU-1001"))
                .andExpect(jsonPath("$.data.items[0].specs.颜色").value("黑"))
                .andExpect(jsonPath("$.data.items[0].imageUrl").value("https://cdn.example.com/1001.png"))
                .andExpect(jsonPath("$.data.items[0].priceFen").value(39900))
                .andExpect(jsonPath("$.data.items[0].priceFenAtAdded").value(39900))
                .andExpect(jsonPath("$.data.items[0].itemStatus").value("VALID"))
                .andExpect(jsonPath("$.data.items[0].stockStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.data.selectedTotalFen").value(39900 * 2))
                .andExpect(jsonPath("$.data.selectedCount").value(1));
        verify(productSkuClient, times(1)).findSnapshots(anyList());
        verify(inventoryAvailabilityClient, times(1)).findAvailability(anyList());
    }

    @Test
    @DisplayName("AC-009/010/011/013 状态矩阵与合计排除：下架/禁用/不存在/调价/缺货/未选中均排除")
    void statusMatrixAndTotalExclusion() throws Exception {
        long member = Long.parseLong(MEMBER);
        cartRepository.add(member, 2001L, 1, 1000L);   // VALID 但缺货
        cartRepository.add(member, 2002L, 1, 1000L);   // 下架
        cartRepository.add(member, 2003L, 1, 1000L);   // 调价（新价 800）
        cartRepository.add(member, 2004L, 1, 1000L);   // 不存在占位
        cartRepository.add(member, 2005L, 3, 1000L);   // VALID+低库存，取消勾选
        cartRepository.add(member, 2006L, 2, 1000L);   // SKU 禁用
        cartRepository.add(member, 2007L, 1, 1000L);   // VALID+充足，计入合计
        cartRepository.selectOne(member, 2005L, false);

        productFixtures.put(2001L, snap(2001, "ON_SALE", "ENABLED", 1000L));
        productFixtures.put(2002L, snap(2002, "OFF_SHELF", "ENABLED", 1000L));
        productFixtures.put(2003L, snap(2003, "ON_SALE", "ENABLED", 800L));
        // 2004 不放 fixture → missing 占位
        productFixtures.put(2005L, snap(2005, "ON_SALE", "ENABLED", 1000L));
        productFixtures.put(2006L, snap(2006, "ON_SALE", "DISABLED", 1000L));
        productFixtures.put(2007L, snap(2007, "ON_SALE", "ENABLED", 1000L));
        stockFixtures.put(2001L, 0L);
        stockFixtures.put(2002L, 50L);
        stockFixtures.put(2003L, 10L);
        stockFixtures.put(2005L, 5L);
        stockFixtures.put(2006L, 50L);
        stockFixtures.put(2007L, 10L);

        expectGetOk()
                .andExpect(jsonPath("$.data.items.length()").value(7))
                .andExpect(jsonPath("$.data.items[?(@.skuId=='2001')].itemStatus").value("VALID"))
                .andExpect(jsonPath("$.data.items[?(@.skuId=='2001')].stockStatus").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.data.items[?(@.skuId=='2002')].itemStatus").value("PRODUCT_OFF_SHELF"))
                .andExpect(jsonPath("$.data.items[?(@.skuId=='2003')].itemStatus").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.data.items[?(@.skuId=='2003')].priceFen").value(800))
                .andExpect(jsonPath("$.data.items[?(@.skuId=='2004')].itemStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data.items[?(@.skuId=='2005')].stockStatus").value("LOW_STOCK"))
                .andExpect(jsonPath("$.data.items[?(@.skuId=='2005')].selected").value(false))
                .andExpect(jsonPath("$.data.items[?(@.skuId=='2006')].itemStatus").value("SKU_INVALID"))
                .andExpect(jsonPath("$.data.items[?(@.skuId=='2007')].itemStatus").value("VALID"))
                // 仅 2007（1000×1）计入；2005 未选中、2001 缺货、2003 调价均排除
                .andExpect(jsonPath("$.data.selectedTotalFen").value(1000))
                .andExpect(jsonPath("$.data.selectedCount").value(6));
    }

    @Test
    @DisplayName("AC-012 product 故障：整车 200，全部 itemStatus=UNKNOWN，库存态照常")
    void productDownDegradesGracefully() throws Exception {
        cartRepository.add(Long.parseLong(MEMBER), 1001L, 2, 39900L);
        productDown = true;
        stockFixtures.put(1001L, 20L);

        expectGetOk()
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].itemStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.items[0].stockStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.data.items[0].priceFen").isEmpty())
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.selectedTotalFen").value(0));
    }

    @Test
    @DisplayName("AC-012 inventory 故障：整车 200，stockStatus=UNKNOWN，商品态不受影响")
    void inventoryDownDegradesGracefully() throws Exception {
        cartRepository.add(Long.parseLong(MEMBER), 1001L, 2, 39900L);
        productFixtures.put(1001L, snap(1001, "ON_SALE", "ENABLED", 39900L));
        inventoryDown = true;

        expectGetOk()
                .andExpect(jsonPath("$.data.items[0].itemStatus").value("VALID"))
                .andExpect(jsonPath("$.data.items[0].stockStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.selectedTotalFen").value(0));
    }

    @Test
    @DisplayName("空车：零 product/inventory 调用直接空响应")
    void emptyCartSkipsDependencies() throws Exception {
        mockMvc.perform(get("/api/mall/cart").header("Authorization", "Bearer " + memberToken(EMPTY_MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.selectedTotalFen").value(0))
                .andExpect(jsonPath("$.data.selectedCount").value(0));
        verify(productSkuClient, never()).findSnapshots(anyList());
        verify(inventoryAvailabilityClient, never()).findAvailability(anyList());
    }

    @Test
    @DisplayName("读车只读：装配前后 Hash 内容与 TTL 均不被改写/续期")
    void readDoesNotMutateRedis() throws Exception {
        long member = Long.parseLong(MEMBER);
        cartRepository.add(member, 1001L, 2, 39900L);
        productFixtures.put(1001L, snap(1001, "ON_SALE", "ENABLED", 39900L));
        stockFixtures.put(1001L, 20L);

        String key = CartConstants.memberKey(member);
        // 等待 2 秒越过整秒边界，确保满 TTL 已开始自然衰减，才能证明读操作没有重新 EXPIRE
        Thread.sleep(2_000);
        Map<Object, Object> before = new LinkedHashMap<>(redisTemplate.opsForHash().entries(key));
        Long ttlBefore = redisTemplate.getExpire(key);
        expectGetOk().andExpect(status().isOk());
        Map<Object, Object> after = redisTemplate.opsForHash().entries(key);
        Long ttlAfter = redisTemplate.getExpire(key);

        assertThat(after).isEqualTo(before);
        // 读操作不 EXPIRE：TTL 只可自然递减（允许同秒相等），绝不可被续期回满 TTL
        assertThat(ttlBefore).isLessThan(CartConstants.TTL_SECONDS);
        assertThat(ttlAfter).isLessThanOrEqualTo(ttlBefore);
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.ResultActions expectGetOk() {
        try {
            return mockMvc.perform(get("/api/mall/cart")
                    .header("Authorization", "Bearer " + memberToken(MEMBER)));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static SkuSnapshot snap(long skuId, String productStatus, String skuStatus, Long price) {
        return new SkuSnapshot(9001L, "演示手机", productStatus, skuId, "SKU-" + skuId, skuStatus,
                price, "https://cdn.example.com/" + skuId + ".png", Map.of("颜色", "黑"),
                "ON_SALE".equals(productStatus) && "ENABLED".equals(skuStatus));
    }

    private static SkuSnapshot missing(long skuId) {
        return new SkuSnapshot(null, null, null, skuId, null, null, null, null, Map.of(), false);
    }

    private String memberToken(String memberId) {
        var claims = JwtClaimsSet.builder()
                .subject(memberId).claim("username", "u" + memberId)
                .claim("subject_type", "MEMBER").claim("auth_version", 1)
                .audience(List.of(ApiTestSecurityConfig.AUDIENCE)).issuer(ApiTestSecurityConfig.ISSUER)
                .issuedAt(java.time.Instant.now().minusSeconds(5))
                .expiresAt(java.time.Instant.now().plusSeconds(600))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}

