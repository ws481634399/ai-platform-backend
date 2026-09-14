package com.ai.mall.product.interfaces.rest.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.product.support.ApiTestSecurityConfig;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * 内部商品查询 API 集成测试：覆盖 CHG-0012 Story 3 test-design TC。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("内部商品查询 API")
class InternalProductApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    private long categoryId;
    private long brandId;

    @BeforeEach
    void setup() {
        jdbc.execute("DELETE FROM product_sku");
        jdbc.execute("DELETE FROM product_attribute");
        jdbc.execute("DELETE FROM product_image");
        jdbc.execute("DELETE FROM product_spu");
        jdbc.execute("DELETE FROM product_category");
        jdbc.execute("DELETE FROM product_brand");

        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(100,0,'数码',1,1,'ENABLED',NOW(),NOW())");
        jdbc.update("INSERT INTO product_brand(id,name,logo,description,sort,status,created_at,updated_at) "
                + "VALUES(200,'Apple','https://cdn.example.com/apple.png','Apple Inc.',1,'ENABLED',NOW(),NOW())");
        categoryId = 100L;
        brandId = 200L;
    }

    @Test
    @DisplayName("内部查询返回完整 ProductSnapshot 字段")
    void internalSnapshotFullFields() throws Exception {
        long productId = createProductWithSkuAndImage("I-SNAP");
        long skuId = jdbc.queryForObject("SELECT id FROM product_sku WHERE sku_code='I-SNAP-SKU'", Long.class);

        mockMvc.perform(get("/api/internal/products/" + productId + "/skus/" + skuId)
                        .header("Authorization", "Bearer " + token(List.of("product:product:detail"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.skuId").value(skuId))
                .andExpect(jsonPath("$.data.productName").value("I-SNAP-name"))
                .andExpect(jsonPath("$.data.skuName").exists())
                .andExpect(jsonPath("$.data.skuAttributes.颜色").value("黑"))
                .andExpect(jsonPath("$.data.price").value(9900))
                .andExpect(jsonPath("$.data.image").exists())
                .andExpect(jsonPath("$.data.currentStatus").value("DRAFT"));
    }

    @Test
    @DisplayName("商品不存在返回 404")
    void internalProductNotFound() throws Exception {
        mockMvc.perform(get("/api/internal/products/99999/skus/1")
                        .header("Authorization", "Bearer " + token(List.of("product:product:detail"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SKU 不属于该商品返回 404")
    void internalSkuNotBelongsToProduct() throws Exception {
        long productId = createProductWithSkuAndImage("I-404");
        mockMvc.perform(get("/api/internal/products/" + productId + "/skus/99999")
                        .header("Authorization", "Bearer " + token(List.of("product:product:detail"))))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private long createProductWithSkuAndImage(String code) throws Exception {
        List<Map<String, Object>> images = List.of(
                imageBody("k1", "https://cdn.example.com/1.png", "MAIN", 0, true));
        Map<String, Object> body = productBody(code, code + "-name", images);
        String resp = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + token(List.of("product:product:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private Map<String, Object> productBody(String code, String name, List<Map<String, Object>> images) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("categoryId", categoryId);
        body.put("brandId", brandId);
        body.put("images", images != null ? images : List.of());
        body.put("attributes", List.of());
        body.put("skus", List.of(skuBody(code + "-SKU", List.of(spec("颜色", "黑")), 9900L)));
        return body;
    }

    private Map<String, Object> imageBody(String key, String url, String type, int sort, boolean main) {
        Map<String, Object> m = new HashMap<>();
        m.put("objectKey", key);
        m.put("imageUrl", url);
        m.put("imageType", type);
        m.put("sortOrder", sort);
        m.put("mainFlag", main);
        return m;
    }

    private Map<String, Object> skuBody(String code, List<Map<String, String>> specs, long price) {
        Map<String, Object> m = new HashMap<>();
        m.put("skuCode", code);
        m.put("specifications", specs);
        m.put("salePriceInCents", price);
        m.put("mainImageUrl", null);
        return m;
    }

    private Map<String, String> spec(String name, String value) {
        Map<String, String> m = new HashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String token(List<String> permissions) {
        var claims = JwtClaimsSet.builder()
                .subject("1002").claim("username", "product_admin")
                .claim("subject_type", "ADMIN").claim("auth_version", 1)
                .audience(List.of("mall-admin-api")).issuer("ai-platform")
                .issuedAt(java.time.Instant.now().minusSeconds(5))
                .expiresAt(java.time.Instant.now().plusSeconds(600));
        if (permissions != null && !permissions.isEmpty()) {
            claims.claim("permissions", permissions);
        }
        return encoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }
}
