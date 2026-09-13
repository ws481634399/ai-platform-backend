package com.ai.mall.product.interfaces.rest.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.product.support.ApiTestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
 * 商品与 SKU 管理端 API 集成测试：H2 + Flyway V1~V3 + 真实安全链，
 * 覆盖 CHG-0011 test-design 主要 TC。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("商品与 SKU 管理 API")
class ProductAdminApiTest {

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
    @DisplayName("TC-001 合法 Product 创建成功，状态 DRAFT")
    void createProductDraft() throws Exception {
        Map<String, Object> body = productBody("P-001", "iPhone 15", null);
        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + token(List.of("product:product:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists());

        mockMvc.perform(get("/api/admin/products")
                        .header("Authorization", "Bearer " + token(List.of("product:product:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data.records[0].name").value("iPhone 15"));
    }

    @Test
    @DisplayName("TC-002 绑定不存在分类拒绝")
    void invalidCategoryRejected() throws Exception {
        Map<String, Object> body = productBody("P-002", "Test", null);
        body.put("categoryId", 99999L);
        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + token(List.of("product:product:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B2143"));
    }

    @Test
    @DisplayName("TC-007 主图唯一：仅一张 mainFlag=true")
    void mainImageUnique() throws Exception {
        List<Map<String, Object>> images = List.of(
                imageBody("k1", "https://cdn.example.com/1.png", "MAIN", 0, true),
                imageBody("k2", "https://cdn.example.com/2.png", "GALLERY", 1, false));
        Map<String, Object> body = productBody("P-003", "With Images", images);

        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + token(List.of("product:product:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());

        long id = jdbc.queryForObject("SELECT id FROM product_spu WHERE product_code='P-003'", Long.class);
        mockMvc.perform(get("/api/admin/products/" + id)
                        .header("Authorization", "Bearer " + token(List.of("product:product:detail"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mainImageUrl").value("https://cdn.example.com/1.png"))
                .andExpect(jsonPath("$.data.images.length()").value(2));
    }

    @Test
    @DisplayName("TC-003/004 SKU 新增成功；编码重复拒绝")
    void createSkuAndDuplicateCode() throws Exception {
        long productId = createProduct("P-004");

        mockMvc.perform(post("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", "Bearer " + token(List.of("product:sku:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(skuBody("SKU-001", List.of(spec("颜色", "黑"), spec("容量", "128G")), 599900L))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", "Bearer " + token(List.of("product:sku:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(skuBody("SKU-001", List.of(spec("颜色", "白"), spec("容量", "256G")), 699900L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("B2162"));
    }

    @Test
    @DisplayName("TC-006 同商品规格组合唯一（顺序不同也拒绝）")
    void duplicateSpecRejected() throws Exception {
        long productId = createProduct("P-005");

        mockMvc.perform(post("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", "Bearer " + token(List.of("product:sku:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(skuBody("SKU-A", List.of(spec("颜色", "黑"), spec("容量", "128G")), 599900L))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", "Bearer " + token(List.of("product:sku:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(skuBody("SKU-B", List.of(spec("容量", "128G"), spec("颜色", "黑")), 599900L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("B2163"));
    }

    @Test
    @DisplayName("TC-005 负价格拒绝；价格为分")
    void negativePriceRejected() throws Exception {
        long productId = createProduct("P-006");

        mockMvc.perform(post("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", "Bearer " + token(List.of("product:sku:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(skuBody("SKU-NEG", List.of(spec("颜色", "黑")), -1L))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", "Bearer " + token(List.of("product:sku:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(skuBody("SKU-OK", List.of(spec("颜色", "黑")), 9900L))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/products/" + productId)
                        .header("Authorization", "Bearer " + token(List.of("product:product:detail"))))
                .andExpect(jsonPath("$.data.skus[0].salePriceInCents").value(9900));
    }

    @Test
    @DisplayName("TC-008 修改 SKU 价格后查询一致")
    void updateSkuPriceConsistent() throws Exception {
        long productId = createProduct("P-007");
        mockMvc.perform(post("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", "Bearer " + token(List.of("product:sku:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(skuBody("SKU-UP", List.of(spec("颜色", "黑")), 10000L))))
                .andExpect(status().isOk());

        long skuId = jdbc.queryForObject("SELECT id FROM product_sku WHERE sku_code='SKU-UP'", Long.class);

        mockMvc.perform(put("/api/admin/products/" + productId + "/skus/" + skuId)
                        .header("Authorization", "Bearer " + token(List.of("product:sku:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("salePriceInCents", 20000L, "mainImageUrl", "https://cdn.example.com/sku.png"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/products/" + productId)
                        .header("Authorization", "Bearer " + token(List.of("product:product:detail"))))
                .andExpect(jsonPath("$.data.skus[0].salePriceInCents").value(20000))
                .andExpect(jsonPath("$.data.skus[0].mainImageUrl").value("https://cdn.example.com/sku.png"));
    }

    @Test
    @DisplayName("TC-013 无 product:product:create 权限 403")
    void permissionEnforced() throws Exception {
        Map<String, Object> body = productBody("P-008", "NoPerm", null);
        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + token(List.of("product:brand:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-015 SKU 独立启停")
    void skuEnableDisable() throws Exception {
        long productId = createProduct("P-009");
        mockMvc.perform(post("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", "Bearer " + token(List.of("product:sku:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(skuBody("SKU-T", List.of(spec("颜色", "黑")), 100L))))
                .andExpect(status().isOk());

        long skuId = jdbc.queryForObject("SELECT id FROM product_sku WHERE sku_code='SKU-T'", Long.class);

        mockMvc.perform(put("/api/admin/products/" + productId + "/skus/" + skuId + "/status")
                        .header("Authorization", "Bearer " + token(List.of("product:sku:disable")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "DISABLED"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/products/" + productId)
                        .header("Authorization", "Bearer " + token(List.of("product:product:detail"))))
                .andExpect(jsonPath("$.data.skus[0].status").value("DISABLED"));
    }

    // ---------- helpers ----------

    private long createProduct(String code) throws Exception {
        Map<String, Object> body = productBody(code, code + "-name", null);
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
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(600));
        if (permissions != null && !permissions.isEmpty()) {
            claims.claim("permissions", permissions);
        }
        return encoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }
}
