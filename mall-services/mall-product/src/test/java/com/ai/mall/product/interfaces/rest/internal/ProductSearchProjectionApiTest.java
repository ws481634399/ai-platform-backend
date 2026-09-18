package com.ai.mall.product.interfaces.rest.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.product.support.ApiTestSecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 搜索投影内部端点测试（CHG-0021）：ON_SALE + EXISTS 启用 SKU 口径、
 * total 与分页 items 同口径、单条 404 语义（不存在/非在架/无启用 SKU）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("搜索投影内部端点")
class ProductSearchProjectionApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Value("${mall.security.internal.shared-secret}")
    String sharedSecret;

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM product_sku");
        jdbc.execute("DELETE FROM product_spu");
        jdbc.execute("DELETE FROM product_category");
        jdbc.execute("DELETE FROM product_brand");
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(100,0,'数码',1,1,'ENABLED',NOW(),NOW())");
        jdbc.update("INSERT INTO product_brand(id,name,logo,description,sort,status,created_at,updated_at) "
                + "VALUES(200,'Apple',NULL,NULL,1,'ENABLED',NOW(),NOW())");

        insertSpu(5001L, "投影手机甲", "ON_SALE");
        insertSpu(5002L, "无启用SKU手机", "ON_SALE");
        insertSpu(5003L, "草稿手机", "DRAFT");
        insertSpu(5004L, "投影手机乙", "ON_SALE");

        insertSku(6001L, 5001L, "S1", 1000L, "ENABLED");
        insertSku(6002L, 5001L, "S2", 3000L, "ENABLED");
        // 5002 仅有 DISABLED SKU → 排除
        insertSku(6003L, 5002L, "S3", 2000L, "DISABLED");
        // 5003 是 DRAFT，即使有启用 SKU 也排除
        insertSku(6004L, 5003L, "S4", 2000L, "ENABLED");
        insertSku(6005L, 5004L, "S5", 4500L, "ENABLED");
    }

    @Test
    @DisplayName("分页投影：仅 ON_SALE 且有启用 SKU 的 2 条，total 同口径，字段含分类/品牌名/价区")
    void projectionPageOnSaleOnly() throws Exception {
        mockMvc.perform(get("/api/internal/products/search-projection")
                        .param("page", "1").param("size", "10")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].productId").value("5001"))
                .andExpect(jsonPath("$.data.items[0].productName").value("投影手机甲"))
                .andExpect(jsonPath("$.data.items[0].keywords").value(""))
                .andExpect(jsonPath("$.data.items[0].categoryId").value(100))
                .andExpect(jsonPath("$.data.items[0].categoryName").value("数码"))
                .andExpect(jsonPath("$.data.items[0].brandId").value(200))
                .andExpect(jsonPath("$.data.items[0].brandName").value("Apple"))
                .andExpect(jsonPath("$.data.items[0].mainImage").value("https://cdn.example.com/5001.png"))
                .andExpect(jsonPath("$.data.items[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.items[0].minPriceFen").value(1000))
                .andExpect(jsonPath("$.data.items[0].maxPriceFen").value(3000))
                .andExpect(jsonPath("$.data.items[0].updatedAt").isNumber())
                .andExpect(jsonPath("$.data.items[1].productId").value("5004"));
    }

    @Test
    @DisplayName("分页按 id 稳定升序，size=1 翻页正确")
    void paginationOrderAndSize() throws Exception {
        mockMvc.perform(get("/api/internal/products/search-projection")
                        .param("page", "1").param("size", "1")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].productId").value("5001"));
        mockMvc.perform(get("/api/internal/products/search-projection")
                        .param("page", "2").param("size", "1")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret))
                .andExpect(jsonPath("$.data.items[0].productId").value("5004"));
    }

    @Test
    @DisplayName("无凭证访问投影端点 → 401")
    void projectionWithoutTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/internal/products/search-projection"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("单条投影：在架返回 200；无启用 SKU / 草稿 / 不存在均 404")
    void singleProjectionAndNotFoundCases() throws Exception {
        mockMvc.perform(get("/api/internal/products/5001/search-projection")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value("5001"))
                .andExpect(jsonPath("$.data.minPriceFen").value(1000));

        mockMvc.perform(get("/api/internal/products/5002/search-projection")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/internal/products/5003/search-projection")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/internal/products/99999/search-projection")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret))
                .andExpect(status().isNotFound());
    }

    private void insertSpu(long id, String name, String status) {
        jdbc.update("INSERT INTO product_spu(id,product_code,product_name,subtitle,description,category_id,"
                + "brand_id,status,min_price,max_price,main_image_url,sales_count,published_at,unpublished_at,"
                + "created_at,updated_at,created_by,updated_by,version,deleted) "
                + "VALUES(?,?,?,?,?,100,200,?,?,?,?,0,NOW(),NULL,NOW(),NOW(),1,1,0,0)",
                id, "P" + id, name, null, null, status, 1000L, 3000L,
                "https://cdn.example.com/" + id + ".png");
    }

    private void insertSku(long id, long productId, String code, long price, String status) {
        jdbc.update("INSERT INTO product_sku(id,product_id,sku_code,sale_price,status,main_image_url,"
                + "specification_data,specification_hash,created_at,updated_at,created_by,updated_by,"
                + "version,deleted) VALUES(?,?,?,?,?,NULL,'[]','h" + id + "',NOW(),NOW(),1,1,0,0)",
                id, productId, code, price, status);
    }
}
