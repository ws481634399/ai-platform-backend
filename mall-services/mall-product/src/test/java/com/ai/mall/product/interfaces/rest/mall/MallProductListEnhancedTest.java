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
 * 商城商品列表增强测试：CHG-0017 STORY-003-02-02-01。
 * 覆盖 brandIds 多选、子孙分类展开、四种排序、size≤50、价区非空。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("商城商品列表增强")
class MallProductListEnhancedTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc.execute("DELETE FROM product_sku");
        jdbc.execute("DELETE FROM product_spu");
        jdbc.execute("DELETE FROM product_category");
    }

    private void insertCategory(long id, long parentId, String name) {
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(?,?,?,1,1,'ENABLED',NOW(),NOW())", id, parentId, name);
    }

    private void insertProduct(long id, String code, String name, long categoryId, long brandId, long price) {
        jdbc.update("INSERT INTO product_spu(id,product_code,product_name,category_id,brand_id,status,"
                + "main_image_url,created_at,updated_at,version,deleted) "
                + "VALUES(?,?,?,?,?,'ON_SALE','img',NOW(),NOW(),0,0)", id, code, name, categoryId, brandId);
        jdbc.update("INSERT INTO product_sku(id,product_id,sku_code,sale_price,status,specification_data,specification_hash,created_at,updated_at,version,deleted) "
                + "VALUES(? ,?,?,?,'ENABLED','{}','h',NOW(),NOW(),0,0)", id * 1000 + 1, id, code + "-S1", price);
    }

    @Test
    @DisplayName("brandIds 多选交集：命中两个品牌商品")
    void brandIdsMultiSelect() throws Exception {
        insertCategory(1, 0, "数码");
        insertProduct(101, "P1", "商品一", 1, 1, 9900);
        insertProduct(102, "P2", "商品二", 1, 2, 19900);
        insertProduct(103, "P3", "商品三", 1, 3, 5000);

        mockMvc.perform(get("/api/mall/products").param("brandIds", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }

    @Test
    @DisplayName("子孙分类：查父分类返回子分类商品")
    void descendantCategory() throws Exception {
        insertCategory(1, 0, "数码");
        insertCategory(11, 1, "手机");
        insertCategory(12, 1, "电脑");
        insertProduct(201, "P1", "手机商品", 11, 1, 9900);
        insertProduct(202, "P2", "电脑商品", 12, 1, 19900);

        mockMvc.perform(get("/api/mall/products").param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }

    @Test
    @DisplayName("排序：price_asc 按最低价升序，price_desc 按最高价降序")
    void sortByPrice() throws Exception {
        insertCategory(1, 0, "数码");
        insertProduct(301, "P1", "低价", 1, 1, 100);
        insertProduct(302, "P2", "中价", 1, 1, 1000);
        insertProduct(303, "P3", "高价", 1, 1, 10000);

        mockMvc.perform(get("/api/mall/products").param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].minPrice").value(100))
                .andExpect(jsonPath("$.data.records[2].minPrice").value(10000));

        mockMvc.perform(get("/api/mall/products").param("sort", "price_desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].minPrice").value(10000))
                .andExpect(jsonPath("$.data.records[2].minPrice").value(100));
    }

    @Test
    @DisplayName("非法 sort 静默回落默认（createdAt DESC）")
    void invalidSortFallback() throws Exception {
        insertCategory(1, 0, "数码");
        insertProduct(401, "P1", "商品一", 1, 1, 100);
        mockMvc.perform(get("/api/mall/products").param("sort", "invalid_sort"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1));
    }

    @Test
    @DisplayName("size>50 收敛为 50")
    void sizeCap50() throws Exception {
        insertCategory(1, 0, "数码");
        insertProduct(501, "P1", "商品一", 1, 1, 100);
        mockMvc.perform(get("/api/mall/products").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50));
    }

    @Test
    @DisplayName("价区 minPrice/maxPrice 非空（number 整数分）")
    void priceRangeNonNull() throws Exception {
        insertCategory(1, 0, "数码");
        insertProduct(601, "P1", "商品一", 1, 1, 9900);
        mockMvc.perform(get("/api/mall/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].minPrice").value(9900))
                .andExpect(jsonPath("$.data.records[0].maxPrice").value(9900));
    }
}
