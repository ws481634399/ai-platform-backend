package com.ai.mall.product.interfaces.rest.mall;

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
 * 商城商品查询 API 集成测试：覆盖 CHG-0012 Story 2 test-design TC。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("商城商品查询 API")
class MallProductApiTest {

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
    @DisplayName("商城列表只返回 ON_SALE 商品")
    void mallListOnlyOnSale() throws Exception {
        long onSaleId = createProductWithSkuAndImage("M-ON");
        publish(onSaleId);
        long draftId = createProductWithSkuAndImage("M-DRAFT");

        mockMvc.perform(get("/api/mall/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(onSaleId));
    }

    @Test
    @DisplayName("商城列表支持分类筛选与分页")
    void mallListFilterAndPage() throws Exception {
        long id = createProductWithSkuAndImage("M-FILTER");
        publish(id);

        mockMvc.perform(get("/api/mall/products").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1));

        mockMvc.perform(get("/api/mall/products").param("categoryId", "99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(0));

        mockMvc.perform(get("/api/mall/products").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    @DisplayName("商城列表按创建时间倒序")
    void mallListOrderByCreatedAtDesc() throws Exception {
        long first = createProductWithSkuAndImage("M-FIRST");
        publish(first);
        Thread.sleep(10);
        long second = createProductWithSkuAndImage("M-SECOND");
        publish(second);

        mockMvc.perform(get("/api/mall/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(second))
                .andExpect(jsonPath("$.data.records[1].id").value(first));
    }

    @Test
    @DisplayName("ON_SALE 商品详情返回完整信息")
    void mallDetailOnSale() throws Exception {
        long id = createProductWithSkuAndImage("M-DETAIL");
        publish(id);

        mockMvc.perform(get("/api/mall/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.skus.length()").value(1))
                .andExpect(jsonPath("$.data.skus[0].salePriceInCents").value(9900));
    }

    @Test
    @DisplayName("非 ON_SALE 商品详情返回 404")
    void mallDetailNotOnSale404() throws Exception {
        long id = createProductWithSkuAndImage("M-404");

        mockMvc.perform(get("/api/mall/products/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("详情响应不含库存字段")
    void mallDetailNoStockField() throws Exception {
        long id = createProductWithSkuAndImage("M-NOS");
        publish(id);

        String body = mockMvc.perform(get("/api/mall/products/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("stock"));
    }

    // ---------- helpers ----------

    private void publish(long id) throws Exception {
        mockMvc.perform(post("/api/admin/products/" + id + "/publish")
                        .header("Authorization", "Bearer " + token(List.of("product:product:publish"))))
                .andExpect(status().isOk());
    }

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
        long productId = objectMapper.readTree(resp).path("data").path("id").asLong();
        mockMvc.perform(post("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", "Bearer " + token(List.of("product:sku:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(skuBody(code + "-SKU", List.of(spec("颜色", "黑")), 9900L))))
                .andExpect(status().isOk());
        return productId;
    }

    private Map<String, Object> productBody(String code, String name, List<Map<String, Object>> images) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("categoryId", categoryId);
        body.put("brandId", brandId);
        body.put("images", images != null ? images : List.of());
        body.put("attributes", List.of());
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
