package com.ai.mall.product.interfaces.rest.mall;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.product.support.ApiTestSecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 商城首页聚合 API 集成测试：CHG-0017 STORY-003-02-01-01。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("商城首页聚合 API")
class MallHomeApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc.execute("DELETE FROM product_sku");
        jdbc.execute("DELETE FROM product_spu");
        jdbc.execute("DELETE FROM product_category");
    }

    @Test
    @DisplayName("首页：分类入口(≤8启用根) + 新品(ON_SALE+启用SKU) + 推荐(fallback) + banners空")
    void homeAggregation() throws Exception {
        // 分类：3 个启用根 + 1 个禁用根
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(1,0,'数码',1,1,'ENABLED',NOW(),NOW())");
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(2,0,'家电',1,2,'ENABLED',NOW(),NOW())");
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(3,0,'服装',1,3,'ENABLED',NOW(),NOW())");
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(4,0,'禁用',1,4,'DISABLED',NOW(),NOW())");

        // 商品 1：ON_SALE + 启用 SKU，价 9900
        jdbc.update("INSERT INTO product_spu(id,product_code,product_name,category_id,brand_id,status,"
                + "main_image_url,created_at,updated_at,version,deleted) "
                + "VALUES(101,'P1','商品一',1,1,'ON_SALE','img1',NOW(),NOW(),0,0)");
        jdbc.update("INSERT INTO product_sku(id,product_id,sku_code,sale_price,status,specification_data,specification_hash,created_at,updated_at,version,deleted) "
                + "VALUES(1001,101,'P1-S1',9900,'ENABLED','{}','h1',NOW(),NOW(),0,0)");

        // 商品 2：ON_SALE + 启用 SKU，价 19900
        jdbc.update("INSERT INTO product_spu(id,product_code,product_name,category_id,brand_id,status,"
                + "main_image_url,created_at,updated_at,version,deleted) "
                + "VALUES(102,'P2','商品二',1,1,'ON_SALE','img2',NOW(),NOW(),0,0)");
        jdbc.update("INSERT INTO product_sku(id,product_id,sku_code,sale_price,status,specification_data,specification_hash,created_at,updated_at,version,deleted) "
                + "VALUES(1002,102,'P2-S1',19900,'ENABLED','{}','h2',NOW(),NOW(),0,0)");

        // 商品 3：ON_SALE 但无启用 SKU → 不应出现
        jdbc.update("INSERT INTO product_spu(id,product_code,product_name,category_id,brand_id,status,"
                + "main_image_url,created_at,updated_at,version,deleted) "
                + "VALUES(103,'P3','商品三无SKU',1,1,'ON_SALE','img3',NOW(),NOW(),0,0)");
        jdbc.update("INSERT INTO product_sku(id,product_id,sku_code,sale_price,status,specification_data,specification_hash,created_at,updated_at,version,deleted) "
                + "VALUES(1003,103,'P3-S1',5000,'DISABLED','{}','h3',NOW(),NOW(),0,0)");

        mockMvc.perform(get("/api/mall/home"))
                .andExpect(status().isOk())
                // 分类入口：仅启用根，3 个
                .andExpect(jsonPath("$.data.categoryEntries.length()").value(3))
                .andExpect(jsonPath("$.data.categoryEntries[0].id").value("1"))
                .andExpect(jsonPath("$.data.categoryEntries[0].name").value("数码"))
                // 新品：仅含启用 SKU 的 ON_SALE 商品，2 个
                .andExpect(jsonPath("$.data.newArrivals.length()").value(2))
                .andExpect(jsonPath("$.data.newArrivals[0].name").exists())
                .andExpect(jsonPath("$.data.newArrivals[0].minPrice").exists())
                .andExpect(jsonPath("$.data.newArrivals[0].maxPrice").exists())
                // 推荐：fallback 同新品，source=FALLBACK_NEWEST
                .andExpect(jsonPath("$.data.recommends.length()").value(2))
                .andExpect(jsonPath("$.data.recommends[0].source").value("FALLBACK_NEWEST"))
                // banners 空数组
                .andExpect(jsonPath("$.data.banners").isArray())
                .andExpect(jsonPath("$.data.banners.length()").value(0));
    }

    @Test
    @DisplayName("空态：无分类无商品时各数组返回 []")
    void homeEmpty() throws Exception {
        mockMvc.perform(get("/api/mall/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryEntries.length()").value(0))
                .andExpect(jsonPath("$.data.newArrivals.length()").value(0))
                .andExpect(jsonPath("$.data.recommends.length()").value(0))
                .andExpect(jsonPath("$.data.banners.length()").value(0));
    }

    @Test
    @DisplayName("分类入口超过 8 个时只取前 8（sort 序）")
    void categoryEntriesLimit8() throws Exception {
        for (int i = 1; i <= 10; i++) {
            jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                            + "VALUES(?,0,'cat'||?,1,?,'ENABLED',NOW(),NOW())",
                    i, i, i);
        }
        mockMvc.perform(get("/api/mall/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryEntries.length()").value(8));
    }
}
