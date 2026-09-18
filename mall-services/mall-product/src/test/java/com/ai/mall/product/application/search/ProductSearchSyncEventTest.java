package com.ai.mall.product.application.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.product.infrastructure.client.SearchSyncClient;
import com.ai.mall.product.support.ApiTestSecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品写操作 → 搜索同步事件测试（CHG-0021）。
 *
 * <p>验证：写接口事务提交后触发 AFTER_COMMIT 监听；监听器重查当前投影，
 * 草稿 → DELETE，上架 → sync(ON_SALE 投影)，下架 → DELETE；
 * search 调用异常被监听器全兜底，写接口本身不受影响。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("商品搜索同步事件")
class ProductSearchSyncEventTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean SearchSyncClient searchSyncClient;

    @BeforeEach
    void clean() {
        jdbc.execute("DELETE FROM product_sku");
        jdbc.execute("DELETE FROM product_spu");
        jdbc.execute("DELETE FROM product_category");
        jdbc.execute("DELETE FROM product_brand");
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(100,0,'数码',1,1,'ENABLED',NOW(),NOW())");
        jdbc.update("INSERT INTO product_brand(id,name,logo,description,sort,status,created_at,updated_at) "
                + "VALUES(200,'Apple',NULL,NULL,1,'ENABLED',NOW(),NOW())");
        clearInvocations(searchSyncClient);
    }

    @Test
    @DisplayName("创建(草稿)→DELETE；发布→sync 投影；下架→DELETE")
    void createPublishUnpublishEventChain() throws Exception {
        long productId = createProduct("EVT-01");

        // CREATE 提交时商品为草稿：投影查无 → DELETE（幂等清理）
        verify(searchSyncClient, after(2000).times(1)).delete(productId);
        verify(searchSyncClient, never()).sync(any());

        mockMvc.perform(post("/api/admin/products/" + productId + "/publish")
                        .header("Authorization", "Bearer " + token("product:product:publish")))
                .andExpect(status().isOk());
        verify(searchSyncClient, after(2000).times(1)).sync(argThatView(productId, "ON_SALE", 8800L));

        mockMvc.perform(post("/api/admin/products/" + productId + "/unpublish")
                        .header("Authorization", "Bearer " + token("product:product:publish")))
                .andExpect(status().isOk());
        verify(searchSyncClient, after(2000).times(2)).delete(productId);
    }

    @Test
    @DisplayName("search 不可达时监听器全兜底：发布接口仍 200，异常不外抛")
    void listenerFailureNeverBreaksWriteApi() throws Exception {
        long productId = createProduct("EVT-02");
        verify(searchSyncClient, after(2000).times(1)).delete(productId);
        // 模拟 search 宕机
        org.mockito.Mockito.doThrow(new RuntimeException("search connection refused"))
                .when(searchSyncClient).sync(any());

        mockMvc.perform(post("/api/admin/products/" + productId + "/publish")
                        .header("Authorization", "Bearer " + token("product:product:publish")))
                .andExpect(status().isOk());
        verify(searchSyncClient, after(2000).times(1)).sync(any());
    }

    private long createProduct(String code) throws Exception {
        Map<String, Object> spec = new HashMap<>();
        spec.put("name", "颜色");
        spec.put("value", "黑");
        Map<String, Object> sku = new HashMap<>();
        sku.put("skuCode", code + "-SKU");
        sku.put("specifications", List.of(spec));
        sku.put("salePriceInCents", 8800L);
        sku.put("mainImageUrl", "https://cdn.example.com/" + code + ".png");
        Map<String, Object> image = new HashMap<>();
        image.put("objectKey", code + "-img");
        image.put("imageUrl", "https://cdn.example.com/" + code + ".png");
        image.put("imageType", "MAIN");
        image.put("sortOrder", 0);
        image.put("mainFlag", true);
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", code + "-name");
        body.put("categoryId", 100);
        body.put("brandId", 200);
        body.put("images", List.of(image));
        body.put("attributes", List.of());
        body.put("skus", List.of(sku));

        String resp = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + token("product:product:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(resp).path("data");
        return data.path("id").asLong(data.asLong());
    }

    private SearchProjectionView argThatView(long productId, String status, long price) {
        return org.mockito.ArgumentMatchers.argThat(view -> view != null
                && view.productId().equals(String.valueOf(productId))
                && status.equals(view.status())
                && view.minPriceFen() != null && view.minPriceFen() == price);
    }

    private String token(String permission) {
        var claims = JwtClaimsSet.builder()
                .subject("1002").claim("username", "product_admin")
                .claim("subject_type", "ADMIN").claim("auth_version", 1)
                .audience(List.of("mall-admin-api")).issuer("ai-platform")
                .issuedAt(java.time.Instant.now().minusSeconds(5))
                .expiresAt(java.time.Instant.now().plusSeconds(600))
                .claim("permissions", List.of(permission));
        return encoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }
}
