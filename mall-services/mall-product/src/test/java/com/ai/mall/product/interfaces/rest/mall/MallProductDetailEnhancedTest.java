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
 * 商品详情增强测试：CHG-0017 STORY-003-02-02-02。
 * 覆盖 specDimensions 归并、skuIndex 组合键、brandName、categoryPath、404 口径。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("商品详情增强")
class MallProductDetailEnhancedTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc.execute("DELETE FROM product_sku");
        jdbc.execute("DELETE FROM product_spu");
        jdbc.execute("DELETE FROM product_category");
        jdbc.execute("DELETE FROM product_brand");
    }

    private void insertCategory(long id, long parentId, String name) {
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(?,?,?,1,1,'ENABLED',NOW(),NOW())", id, parentId, name);
    }

    private void insertBrand(long id, String name) {
        jdbc.update("INSERT INTO product_brand(id,name,logo,description,sort,status,created_at,updated_at) "
                + "VALUES(?,?,'logo','desc',1,'ENABLED',NOW(),NOW())", id, name);
    }

    private void insertProduct(long id, String code, String name, long categoryId, long brandId) {
        jdbc.update("INSERT INTO product_spu(id,product_code,product_name,category_id,brand_id,status,"
                + "main_image_url,created_at,updated_at,version,deleted) "
                + "VALUES(?,?,?,?,?,'ON_SALE','img',NOW(),NOW(),0,0)", id, code, name, categoryId, brandId);
    }

    private void insertSku(long id, long productId, String code, long price, String status, String specJson) {
        jdbc.update("INSERT INTO product_sku(id,product_id,sku_code,sale_price,status,specification_data,specification_hash,created_at,updated_at,version,deleted) "
                + "VALUES(?,?,?,?,?,?,?,NOW(),NOW(),0,0)", id, productId, code, price, status, specJson, "h" + id);
    }

    @Test
    @DisplayName("详情：brandName + categoryPath + specDimensions + skuIndex")
    void detailWithMatrix() throws Exception {
        insertCategory(1, 0, "数码");
        insertCategory(11, 1, "手机");
        insertBrand(200, "Apple");
        insertProduct(101, "P1", "iPhone", 11, 200);
        // 两个规格维度：颜色、尺码
        insertSku(1001, 101, "S1", 9900, "ENABLED", "{\"颜色\":\"红\",\"尺码\":\"L\"}");
        insertSku(1002, 101, "S2", 10900, "ENABLED", "{\"颜色\":\"蓝\",\"尺码\":\"L\"}");
        insertSku(1003, 101, "S3", 11900, "ENABLED", "{\"颜色\":\"红\",\"尺码\":\"XL\"}");

        mockMvc.perform(get("/api/mall/products/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.brandName").value("Apple"))
                // categoryPath：根在前 [数码, 手机]
                .andExpect(jsonPath("$.data.categoryPath.length()").value(2))
                .andExpect(jsonPath("$.data.categoryPath[0].name").value("数码"))
                .andExpect(jsonPath("$.data.categoryPath[1].name").value("手机"))
                // dimensionsOrder 首次出现序
                .andExpect(jsonPath("$.data.dimensionsOrder[0]").value("颜色"))
                .andExpect(jsonPath("$.data.dimensionsOrder[1]").value("尺码"))
                // specDimensions 去重保序
                .andExpect(jsonPath("$.data.specDimensions[0].name").value("颜色"))
                .andExpect(jsonPath("$.data.specDimensions[0].values[0]").value("红"))
                .andExpect(jsonPath("$.data.specDimensions[0].values[1]").value("蓝"))
                // skuIndex 组合键
                .andExpect(jsonPath("$.data.skuIndex['红|L'].skuId").value("1001"))
                .andExpect(jsonPath("$.data.skuIndex['红|L'].priceFen").value(9900))
                .andExpect(jsonPath("$.data.skuIndex['蓝|L'].skuId").value("1002"));
    }

    @Test
    @DisplayName("404：不存在的商品")
    void detailNotFound() throws Exception {
        mockMvc.perform(get("/api/mall/products/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("404：无启用 SKU 的商品（不泄露存在性）")
    void detailNoEnabledSku() throws Exception {
        insertCategory(1, 0, "数码");
        insertBrand(200, "Apple");
        insertProduct(101, "P1", "无SKU商品", 1, 200);
        insertSku(1001, 101, "S1", 9900, "DISABLED", "{\"颜色\":\"红\"}");

        mockMvc.perform(get("/api/mall/products/101"))
                .andExpect(status().isNotFound());
    }
}
