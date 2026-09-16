package com.ai.mall.cart.interfaces.rest.mall;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.cart.application.cart.ProductSkuClient;
import com.ai.mall.cart.application.cart.ProductSkuClient.SkuSnapshot;
import com.ai.mall.cart.domain.cart.CartErrorCode;
import com.ai.mall.cart.support.AbstractRedisIntegrationTest;
import com.ai.mall.cart.support.ApiTestSecurityConfig;
import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.common.web.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 购物车接口全链路测试（CHG-0018 DU-BE-801）：真实 Redis + 测试安全链 + mock product 契约。
 * 覆盖 AC-003/005/006/007 与错误码矩阵、会员归属隔离、M4 内部端点凭证。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(ApiTestSecurityConfig.class)
class CartApiTest extends AbstractRedisIntegrationTest {

    private static final String MEMBER_A = "5001";
    private static final String MEMBER_B = "5002";
    private static final String INTERNAL_SECRET = "dev-internal-secret";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private ProductSkuClient productSkuClient;

    @MockitoBean
    private com.ai.mall.cart.application.cart.InventoryAvailabilityClient inventoryAvailabilityClient;

    @BeforeEach
    void cleanCarts() {
        redisTemplate.delete(redisTemplate.keys("cart:member:*"));
        lenient().when(productSkuClient.findSnapshots(anyList())).thenAnswer(invocation -> {
            Long skuId = invocation.<List<Long>>getArgument(0).get(0);
            // 7003：product 依赖故障 → 503；7002：业务不可售 → 400
            if (skuId == 7003L) {
                throw new BusinessException(CartErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
            }
            boolean salable = skuId != 7002L;
            return List.of(new SkuSnapshot(salable ? 9001L : null, salable ? "演示手机" : null,
                    salable ? "ON_SALE" : "OFF_SHELF", skuId, "SKU-" + skuId,
                    salable ? "ENABLED" : "DISABLED", salable ? 39900L : null,
                    salable ? "https://cdn.example.com/sku.png" : null,
                    Map.of("颜色", "黑"), salable));
        });
        // DU-BE-802：GET 读模型需要库存聚合，测试统一给充足库存（本类不验证库存语义）
        lenient().when(inventoryAvailabilityClient.findAvailability(anyList())).thenAnswer(invocation ->
                invocation.<List<Long>>getArgument(0).stream()
                        .map(id -> new com.ai.mall.cart.application.cart.InventoryAvailabilityClient
                                .SkuAvailability(id, 50L))
                        .toList());
    }

    // ---------- AC-007 鉴权 ----------

    @Test
    @DisplayName("AC-007 无 Token → 401")
    void noTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/mall/cart")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC-007 ADMIN Token → 403（越界）")
    void adminForbidden() throws Exception {
        mockMvc.perform(get("/api/mall/cart").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("M4 内部端点无凭证/错误凭证/会员 JWT 一律 401 INTERNAL_UNAUTHORIZED（内部链只认共享凭证）")
    void internalEndpointRequiresInternalToken() throws Exception {
        mockMvc.perform(get("/api/internal/carts/members/1/selected-items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_UNAUTHORIZED"));
        mockMvc.perform(get("/api/internal/carts/members/1/selected-items")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_UNAUTHORIZED"));
        mockMvc.perform(get("/api/internal/carts/members/1/selected-items")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 加购主链与错误矩阵 ----------

    @Test
    @DisplayName("AC-001 会员加购成功：skuId 字符串、快照价、默认勾选")
    void addItemOk() throws Exception {
        mockMvc.perform(post("/api/mall/cart/items").header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"skuId":"1001","quantity":2}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].skuId").value("1001"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].selected").value(true))
                .andExpect(jsonPath("$.data.items[0].priceFenAtAdded").value(39900));
    }

    @Test
    @DisplayName("AC-003 下架/失效 SKU → 400 SKU_NOT_SALABLE，车不变")
    void addUnsalableRejected() throws Exception {
        mockMvc.perform(post("/api/mall/cart/items").header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"skuId":"7002","quantity":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CartErrorCode.SKU_NOT_SALABLE.getCode()));
        mockMvc.perform(get("/api/mall/cart").header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("product 依赖故障 → 503 DEPENDENCY_UNAVAILABLE（区别于不可售）")
    void addDependencyFailure503() throws Exception {
        mockMvc.perform(post("/api/mall/cart/items").header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"skuId":"7003","quantity":1}"""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(CartErrorCode.DEPENDENCY_UNAVAILABLE.getCode()));
    }

    @Test
    @DisplayName("非法数量 0/1000 与非法 skuId → 400，不触达 product 契约")
    void invalidRequestRejected() throws Exception {
        mockMvc.perform(post("/api/mall/cart/items").header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"skuId":"1001","quantity":1000}"""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/mall/cart/items").header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"skuId":"abc","quantity":1}"""))
                .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(productSkuClient);
    }

    @Test
    @DisplayName("AC-002 同 SKU 合并；合并超 999 → 400 CART_QUANTITY_LIMIT 且保持 999")
    void mergeAndQuantityLimit() throws Exception {
        add(MEMBER_A, 1001, 999);
        mockMvc.perform(post("/api/mall/cart/items").header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"skuId":"1001","quantity":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CartErrorCode.CART_QUANTITY_LIMIT.getCode()));
        mockMvc.perform(get("/api/mall/cart").header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(jsonPath("$.data.items[0].quantity").value(999));
    }

    @Test
    @DisplayName("AC-004 第 101 个不同 SKU → 400 CART_ITEMS_LIMIT，车保持 100 条")
    void itemsLimit() throws Exception {
        for (long skuId = 2001; skuId <= 2100; skuId++) {
            add(MEMBER_A, skuId, 1);
        }
        mockMvc.perform(post("/api/mall/cart/items").header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"skuId":"2101","quantity":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CartErrorCode.CART_ITEMS_LIMIT.getCode()));
        mockMvc.perform(get("/api/mall/cart").header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(jsonPath("$.data.items.length()").value(100));
    }

    // ---------- 改/删/选 ----------

    @Test
    @DisplayName("AC-005 改量不存在 404；单删 204 幂等；批删忽略不存在项")
    void updateAndRemove() throws Exception {
        add(MEMBER_A, 1001, 1);
        add(MEMBER_A, 1002, 1);
        add(MEMBER_A, 1003, 1);

        mockMvc.perform(put("/api/mall/cart/items/4040").header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quantity":3}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(CartErrorCode.CART_ITEM_NOT_FOUND.getCode()));

        mockMvc.perform(put("/api/mall/cart/items/1001").header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quantity":3}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.skuId=='1001')].quantity").value(3));

        mockMvc.perform(delete("/api/mall/cart/items/1001").header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/mall/cart/items/1001").header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/mall/cart/items/batch-delete")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"skuIds":["1002","4040"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].skuId").value("1003"));
    }

    @Test
    @DisplayName("AC-006 单选/全选/取消跨操作保持")
    void selectionLifecycle() throws Exception {
        add(MEMBER_A, 1001, 1);
        add(MEMBER_A, 1002, 1);

        mockMvc.perform(post("/api/mall/cart/items/1001/unselect")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.selected==true)]", org.hamcrest.Matchers.hasSize(1)));
        mockMvc.perform(get("/api/mall/cart").header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(jsonPath("$.data.items[?(@.skuId=='1002' && @.selected==true)]",
                        org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(post("/api/mall/cart/unselect-all").header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.selected==true)]", org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(post("/api/mall/cart/select-all").header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.selected==false)]", org.hamcrest.Matchers.hasSize(0)));
    }

    // ---------- AC-007 归属隔离 + M4 选中项 ----------

    @Test
    @DisplayName("AC-007 会员只能操作本人车：A 的车对 B 不可见")
    void cartIsolation() throws Exception {
        add(MEMBER_A, 1001, 1);
        mockMvc.perform(get("/api/mall/cart").header("Authorization", "Bearer " + memberToken(MEMBER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("M4 selected-items：凭证正确时仅返回勾选条目，ID 为字符串")
    void selectedItemsForOrder() throws Exception {
        add(MEMBER_A, 1001, 2);
        add(MEMBER_A, 1002, 3);
        mockMvc.perform(post("/api/mall/cart/items/1002/unselect")
                .header("Authorization", "Bearer " + memberToken(MEMBER_A))).andExpect(status().isOk());

        mockMvc.perform(get("/api/internal/carts/members/" + MEMBER_A + "/selected-items")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].skuId").value("1001"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));
    }

    // ---------- helpers ----------

    private void add(String memberId, long skuId, int quantity) throws Exception {
        mockMvc.perform(post("/api/mall/cart/items").header("Authorization", "Bearer " + memberToken(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":\"" + skuId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private String memberToken(String memberId) {
        return token(memberId, "MEMBER");
    }

    private String adminToken() {
        return token("1002", "ADMIN");
    }

    private String token(String subject, String subjectType) {
        var claims = JwtClaimsSet.builder()
                .subject(subject).claim("username", "u" + subject)
                .claim("subject_type", subjectType).claim("auth_version", 1)
                .audience(List.of(ApiTestSecurityConfig.AUDIENCE)).issuer(ApiTestSecurityConfig.ISSUER)
                .issuedAt(java.time.Instant.now().minusSeconds(5))
                .expiresAt(java.time.Instant.now().plusSeconds(600))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
