package com.ai.mall.product.interfaces.rest.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;

/**
 * 商城商品查询 API 集成测试：覆盖 CHG-0012/CHG-0015 test-design TC
 * （字符串 ID、价区聚合、EXISTS 有效 SKU 过滤、双形态入参）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("商城商品查询 API")
class MallProductApiTest {

    /** JS Number.MAX_SAFE_INTEGER：雪花 ID 必然大于此值，若以 number 输出必丢精度。 */
    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

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
                .andExpect(jsonPath("$.data.records[0].id").value(String.valueOf(onSaleId)));
        assertThat(draftId).isGreaterThan(0L);
    }

    @Test
    @DisplayName("商城列表支持分类筛选与分页")
    void mallListFilterAndPage() throws Exception {
        long id = createProductWithSkuAndImage("M-FILTER");
        publish(id);

        // TC-008：query 参数 ID 字符串形态等价
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
                .andExpect(jsonPath("$.data.records[0].id").value(String.valueOf(second)))
                .andExpect(jsonPath("$.data.records[1].id").value(String.valueOf(first)));
    }

    @Test
    @DisplayName("ON_SALE 商品详情返回完整信息")
    void mallDetailOnSale() throws Exception {
        long id = createProductWithSkuAndImage("M-DETAIL");
        publish(id);

        JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/mall/products/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(body.path("data").path("id").isTextual()).isTrue();
        assertThat(body.path("data").path("id").asText()).isEqualTo(String.valueOf(id));
        assertThat(body.path("data").path("status").asText()).isEqualTo("ON_SALE");
        assertThat(body.path("data").path("skus").get(0).path("salePriceInCents").isNumber()).isTrue();
        assertThat(body.path("data").path("skus").get(0).path("salePriceInCents").asLong()).isEqualTo(9900L);
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

    @Test
    @DisplayName("TC-006 商品列表 id 节点为字符串，雪花值不丢精度")
    void listIdsAreStringsWithoutPrecisionLoss() throws Exception {
        long id = createProductWithSkuAndImage("M-SID");
        publish(id);
        assertThat(id).isGreaterThan(JS_MAX_SAFE_INTEGER);

        JsonNode tree = objectMapper.readTree(mockMvc.perform(get("/api/mall/products"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode item = tree.path("data").path("records").get(0);
        assertThat(item.path("id").isTextual()).isTrue();
        assertThat(item.path("id").asText()).isEqualTo(String.valueOf(id));
        assertThat(item.path("categoryId").isTextual()).isTrue();
        assertThat(item.path("brandId").isTextual()).isTrue();
    }

    @Test
    @DisplayName("TC-007 嵌套业务 ID 全为字符串，金额/分页保持 number")
    void nestedIdsStringAndNumbersNumeric() throws Exception {
        long id = createProductWithSkuAndImage("M-NEST");
        publish(id);

        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/api/mall/products/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(detail.path("categoryId").isTextual()).isTrue();
        assertThat(detail.path("brandId").isTextual()).isTrue();
        assertThat(detail.path("images").get(0).path("id").isTextual()).isTrue();
        assertThat(detail.path("attributes").get(0).path("id").isTextual()).isTrue();
        assertThat(detail.path("skus").get(0).path("id").isTextual()).isTrue();
        // 金额仍为 number
        assertThat(detail.path("skus").get(0).path("salePriceInCents").isNumber()).isTrue();

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/mall/products"))
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(list.path("total").isNumber()).isTrue();
        assertThat(list.path("page").isNumber()).isTrue();
        assertThat(list.path("size").isNumber()).isTrue();
    }

    @Test
    @DisplayName("TC-008 请求体 ID 传字符串 \"100\" 与数字 100 等价")
    void requestBodyStringIdAccepted() throws Exception {
        Map<String, Object> body = productBody("M-STRID", "M-STRID-name", null);
        body.put("categoryId", String.valueOf(categoryId));
        body.put("brandId", String.valueOf(brandId));

        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + token(List.of("product:product:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-009 列表 minPrice/maxPrice 为启用 SKU 的真实整数分（MIN/MAX）")
    void listPriceRangeFromEnabledSkus() throws Exception {
        long id = createProductWithTwoSkus("M-PRICE", 5000L, 19900L);
        publish(id);

        JsonNode item = objectMapper.readTree(mockMvc.perform(get("/api/mall/products"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("records").get(0);
        assertThat(item.path("minPrice").isNumber()).isTrue();
        assertThat(item.path("maxPrice").isNumber()).isTrue();
        assertThat(item.path("minPrice").asLong()).isEqualTo(5000L);
        assertThat(item.path("maxPrice").asLong()).isEqualTo(19900L);
    }

    @Test
    @DisplayName("TC-010 全部 SKU 禁用后商品从商城列表消失；重新启用后恢复")
    void productDisappearsWhenAllSkusDisabled() throws Exception {
        long id = createProductWithTwoSkus("M-EXISTS", 1000L, 2000L);
        publish(id);
        long sku1 = jdbc.queryForObject("SELECT id FROM product_sku WHERE sku_code='M-EXISTS-SKU-1'", Long.class);
        long sku2 = jdbc.queryForObject("SELECT id FROM product_sku WHERE sku_code='M-EXISTS-SKU-2'", Long.class);

        mockMvc.perform(get("/api/mall/products"))
                .andExpect(jsonPath("$.data.records.length()").value(1));

        changeSkuStatus(id, sku1, "DISABLED");
        // 仍有一个启用 SKU：依旧可见，价区只统计剩余启用 SKU
        JsonNode stillVisible = objectMapper.readTree(mockMvc.perform(get("/api/mall/products"))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andReturn().getResponse().getContentAsString());
        assertThat(stillVisible.path("data").path("records").get(0).path("minPrice").asLong()).isEqualTo(2000L);

        changeSkuStatus(id, sku2, "DISABLED");
        mockMvc.perform(get("/api/mall/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(0));
        // 无启用 SKU 时详情同样不可售（404）
        mockMvc.perform(get("/api/mall/products/" + id))
                .andExpect(status().isNotFound());

        changeSkuStatus(id, sku1, "ENABLED");
        mockMvc.perform(get("/api/mall/products"))
                .andExpect(jsonPath("$.data.records.length()").value(1));
    }

    // ---------- helpers ----------

    private void publish(long id) throws Exception {
        mockMvc.perform(post("/api/admin/products/" + id + "/publish")
                        .header("Authorization", "Bearer " + token(List.of("product:product:publish"))))
                .andExpect(status().isOk());
    }

    private void changeSkuStatus(long productId, long skuId, String status) throws Exception {
        mockMvc.perform(put("/api/admin/products/" + productId + "/skus/" + skuId + "/status")
                        .header("Authorization", "Bearer " + token(List.of("product:sku:disable")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    private long createProductWithSkuAndImage(String code) throws Exception {
        Map<String, Object> body = productBody(code, code + "-name",
                List.of(skuBody(code + "-SKU", List.of(spec("颜色", "黑")), 9900L)));
        return postProduct(code, body);
    }

    private long createProductWithTwoSkus(String code, long price1, long price2) throws Exception {
        Map<String, Object> body = productBody(code, code + "-name", List.of(
                skuBody(code + "-SKU-1", List.of(spec("颜色", "黑")), price1),
                skuBody(code + "-SKU-2", List.of(spec("颜色", "白")), price2)));
        return postProduct(code, body);
    }

    private long postProduct(String code, Map<String, Object> body) throws Exception {
        String resp = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + token(List.of("product:product:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private Map<String, Object> productBody(String code, String name, List<Map<String, Object>> skus) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("categoryId", categoryId);
        body.put("brandId", brandId);
        body.put("images", List.of(imageBody("k1", "https://cdn.example.com/1.png", "MAIN", 0, true)));
        body.put("attributes", List.of(attrBody("型号", "旗舰", 0)));
        body.put("skus", skus != null ? skus
                : List.of(skuBody(code + "-SKU", List.of(spec("颜色", "黑")), 9900L)));
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

    private Map<String, Object> attrBody(String name, String value, int sort) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("value", value);
        m.put("sortOrder", sort);
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
