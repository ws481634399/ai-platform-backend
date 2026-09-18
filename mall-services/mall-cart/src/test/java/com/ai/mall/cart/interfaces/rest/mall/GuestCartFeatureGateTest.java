package com.ai.mall.cart.interfaces.rest.mall;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.cart.application.cart.InventoryAvailabilityClient;
import com.ai.mall.cart.application.cart.ProductSkuClient;
import com.ai.mall.cart.application.cart.ProductSkuClient.SkuSnapshot;
import com.ai.mall.cart.support.AbstractRedisIntegrationTest;
import com.ai.mall.cart.support.ApiTestSecurityConfig;
import com.ai.mall.common.config.FeatureDisabledException;
import com.ai.mall.common.config.FeatureGate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;

/**
 * CHG-0022 Story3 游客购物车开关切点测试：
 * 关闭时游客车服务端入口（merge-token/merge）→ 403 B0606；会员加购不受开关影响。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(ApiTestSecurityConfig.class)
class GuestCartFeatureGateTest extends AbstractRedisIntegrationTest {

    private static final String MEMBER = "5001";
    private static final String FEATURE = "mall.guest-cart.enabled";

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired StringRedisTemplate redisTemplate;

    @MockitoBean FeatureGate featureGate;
    @MockitoBean ProductSkuClient productSkuClient;
    @MockitoBean InventoryAvailabilityClient inventoryAvailabilityClient;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(redisTemplate.keys("cart:member:*"));
        lenient().when(productSkuClient.findSnapshots(anyList())).thenAnswer(invocation ->
                invocation.<List<Long>>getArgument(0).stream()
                        .map(skuId -> new SkuSnapshot(9001L, "演示手机", "ON_SALE", skuId, "SKU-" + skuId,
                                "ENABLED", 39900L, "https://cdn.example.com/sku.png", Map.of("颜色", "黑"), true))
                        .toList());
        lenient().when(inventoryAvailabilityClient.findAvailability(anyList())).thenAnswer(invocation ->
                invocation.<List<Long>>getArgument(0).stream()
                        .map(id -> new InventoryAvailabilityClient.SkuAvailability(id, 50L))
                        .toList());
    }

    @Test
    @DisplayName("S3-TC-004a：游客车关闭 → merge-token/merge 返回 403 B0606")
    void guestCartDisabledRejectsMergeEndpoints() throws Exception {
        doThrow(new FeatureDisabledException(FEATURE))
                .when(featureGate).ensureEnabled(eq(FEATURE), anyBoolean());

        mockMvc.perform(post("/api/mall/cart/merge-token")
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("B0606"));

        String body = """
                {"mergeToken":"any-token","items":[{"skuId":"1001","quantity":1}]}""";
        mockMvc.perform(post("/api/mall/cart/merge")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("B0606"));
    }

    @Test
    @DisplayName("S3-TC-004b：游客车开启（mock 放行）→ merge-token 200；会员加购不受影响")
    void guestCartEnabledAndMemberUnaffected() throws Exception {
        doNothing().when(featureGate).ensureEnabled(eq(FEATURE), anyBoolean());

        // merge-token 正常签发 200（merge 本体因 token 不匹配会失败，开关切点已放行即可证明）
        mockMvc.perform(post("/api/mall/cart/merge-token")
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mergeToken").isNotEmpty());

        // 会员加购完全不经过游客车开关
        mockMvc.perform(post("/api/mall/cart/items")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuId":"1001","quantity":2}"""))
                .andExpect(status().isOk());
    }

    private String memberToken() {
        var claims = JwtClaimsSet.builder()
                .subject(MEMBER).claim("username", "u" + MEMBER)
                .claim("subject_type", "MEMBER").claim("auth_version", 1)
                .audience(List.of(ApiTestSecurityConfig.AUDIENCE)).issuer(ApiTestSecurityConfig.ISSUER)
                .issuedAt(java.time.Instant.now().minusSeconds(5))
                .expiresAt(java.time.Instant.now().plusSeconds(600))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
