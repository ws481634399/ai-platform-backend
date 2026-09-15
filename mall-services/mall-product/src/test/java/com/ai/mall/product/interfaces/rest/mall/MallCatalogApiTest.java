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
 * 商城公开分类/品牌 API 集成测试：CHG-0017 STORY-003-02-01-02 TC-001~004。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("商城公开分类与品牌 API")
class MallCatalogApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc.execute("DELETE FROM product_category");
        jdbc.execute("DELETE FROM product_brand");
    }

    @Test
    @DisplayName("TC-001: 分类树仅含启用节点，禁用父整枝剪除（含启用子），按 sort")
    void categoryTreeEnabledOnlyAndPrune() throws Exception {
        // 根：数码(启用, sort=1)、家电(禁用, sort=2)
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(1,0,'数码',1,1,'ENABLED',NOW(),NOW())");
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(2,0,'家电',1,2,'DISABLED',NOW(),NOW())");
        // 数码子：手机(启用)、平板(禁用)
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(11,1,'手机',2,1,'ENABLED',NOW(),NOW())");
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(12,1,'平板',2,2,'DISABLED',NOW(),NOW())");
        // 家电子：冰箱(启用) —— 应随禁用父整枝剪除
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,sort,status,created_at,updated_at) "
                + "VALUES(21,2,'冰箱',2,1,'ENABLED',NOW(),NOW())");

        mockMvc.perform(get("/api/mall/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("1"))
                .andExpect(jsonPath("$.data[0].name").value("数码"))
                .andExpect(jsonPath("$.data[0].children.length()").value(1))
                .andExpect(jsonPath("$.data[0].children[0].id").value("11"))
                .andExpect(jsonPath("$.data[0].children[0].name").value("手机"));
    }

    @Test
    @DisplayName("TC-004: 空分类树返回 []")
    void emptyCategoryTree() throws Exception {
        mockMvc.perform(get("/api/mall/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("TC-002: 品牌仅启用，keyword 模糊，size>200 收敛 200，id 字符串")
    void brandsEnabledKeywordAndSizeCap() throws Exception {
        jdbc.update("INSERT INTO product_brand(id,name,logo,description,sort,status,created_at,updated_at) "
                + "VALUES(1,'Apple','logo1','desc',1,'ENABLED',NOW(),NOW())");
        jdbc.update("INSERT INTO product_brand(id,name,logo,description,sort,status,created_at,updated_at) "
                + "VALUES(2,'Samsung','logo2','desc',2,'ENABLED',NOW(),NOW())");
        jdbc.update("INSERT INTO product_brand(id,name,logo,description,sort,status,created_at,updated_at) "
                + "VALUES(3,'Xiaomi','logo3','desc',3,'DISABLED',NOW(),NOW())");

        // 仅启用品牌
        mockMvc.perform(get("/api/mall/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value("1"))
                .andExpect(jsonPath("$.data.items[0].name").value("Apple"));

        // keyword 模糊
        mockMvc.perform(get("/api/mall/brands").param("keyword", "app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Apple"));

        // size>200 收敛为 200
        mockMvc.perform(get("/api/mall/brands").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(200));
    }

    @Test
    @DisplayName("TC-004: 空品牌结果返回 [] 结构")
    void emptyBrands() throws Exception {
        mockMvc.perform(get("/api/mall/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("keyword 通配符 % 被转义，不当作 LIKE 通配")
    void brandKeywordWildcardEscaped() throws Exception {
        jdbc.update("INSERT INTO product_brand(id,name,logo,description,sort,status,created_at,updated_at) "
                + "VALUES(1,'100%棉','logo','desc',1,'ENABLED',NOW(),NOW())");
        jdbc.update("INSERT INTO product_brand(id,name,logo,description,sort,status,created_at,updated_at) "
                + "VALUES(2,'纯棉','logo','desc',2,'ENABLED',NOW(),NOW())");

        // 搜索 "100%棉" 应精确匹配含该字面的品牌，不把 % 当通配
        mockMvc.perform(get("/api/mall/brands").param("keyword", "100%棉"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("100%棉"));
    }
}
