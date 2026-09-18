package com.ai.mall.search.api;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.ai.mall.search.domain.search.SearchProductDocument;
import com.ai.mall.search.domain.search.SearchQuery;
import com.ai.mall.search.domain.search.SortMode;
import com.ai.mall.search.infrastructure.elasticsearch.ElasticsearchProductSearchAdapter;
import com.ai.mall.search.support.AbstractElasticsearchTest;
import com.ai.mall.search.support.SearchApiTestSecurityConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CHG-0020 DU-BE-502/511 查询链路集成测试（真实 ES 8.17.4）。
 *
 * <p>覆盖：相关性/名称加权、仅在售过滤、响应白名单、分页、无词浏览、
 * 类目品牌过滤、价格区间相交、四种排序、参数 400、空结果 200 无 ERROR。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SearchApiTestSecurityConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductSearchApiTest extends AbstractElasticsearchTest {

    private static final String INDEX = "mall_products_it_search";

    @DynamicPropertySource
    static void registerIndex(DynamicPropertyRegistry registry) {
        // 与 IndexSyncIntegrationTest 的默认 mall_products 别名隔离：直连物理索引模式（同名不挂别名）
        registry.add("mall.search.index-alias", () -> INDEX);
        registry.add("mall.search.physical-index", () -> INDEX);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ElasticsearchClient client;

    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

    @BeforeAll
    void seedIndex() throws Exception {
        if (client.indices().exists(e -> e.index(INDEX)).value()) {
            client.indices().delete(d -> d.index(INDEX));
        }
        Map<String, Property> mapping = new LinkedHashMap<>();
        mapping.put("productName", Property.of(p -> p.text(t -> t)));
        mapping.put("keywords", Property.of(p -> p.text(t -> t)));
        mapping.put("brandName", Property.of(p -> p.text(t -> t)));
        mapping.put("categoryName", Property.of(p -> p.text(t -> t)));
        mapping.put("status", Property.of(p -> p.keyword(k -> k)));
        mapping.put("categoryId", Property.of(p -> p.long_(l -> l)));
        mapping.put("brandId", Property.of(p -> p.long_(l -> l)));
        mapping.put("minPrice", Property.of(p -> p.long_(l -> l)));
        mapping.put("maxPrice", Property.of(p -> p.long_(l -> l)));
        mapping.put("publishedAt", Property.of(p -> p.date(d -> d)));
        mapping.put("updatedAt", Property.of(p -> p.date(d -> d)));
        mapping.put("mainImage", Property.of(p -> p.keyword(k -> k.index(false))));
        client.indices().create(c -> c.index(INDEX).mappings(m -> m.properties(mapping)));

        List<BulkOperation> ops = new ArrayList<>();
        addDoc(ops, 1L, "Mechanical Keyboard 87", "外设 青轴", 10L, "电脑配件",
                100L, "KeyPro", "img/1.jpg", "ON_SALE", 19900L, 25900L, 1_750_000_000_000L,
                1_760_000_000_001L);
        addDoc(ops, 2L, "Mouse Pad XL", "keyboard desk mat", 10L, "电脑配件",
                200L, "DeskGear", "img/2.jpg", "ON_SALE", 3900L, 3900L, 1_740_000_000_000L,
                1_760_000_000_002L);
        addDoc(ops, 3L, "机械键盘 Pro", "热插拔 客制化", 10L, "电脑配件",
                100L, "KeyPro", "img/3.jpg", "ON_SALE", 30000L, 30000L, 1_730_000_000_000L,
                1_760_000_000_003L);
        addDoc(ops, 4L, "Mechanical Keyboard OLD", "legacy", 10L, "电脑配件",
                100L, "KeyPro", "img/4.jpg", "OFF_SALE", 9900L, 9900L, 1_720_000_000_000L,
                1_760_000_000_004L);
        addDoc(ops, 5L, "KeyPro Switch Opener", "tool", 20L, "工具",
                100L, "KeyPro", "img/5.jpg", "ON_SALE", 990L, 990L, 1_710_000_000_000L,
                1_760_000_000_005L);
        addDoc(ops, 6L, "No Date Item", "misc", 10L, "电脑配件",
                300L, "NoBrand", "img/6.jpg", "ON_SALE", 5000L, 5000L, null,
                1_760_000_000_006L);
        for (long i = 0; i < 100; i++) {
            // 种子数据 updatedAt 显式早于精选 6 条，保证默认浏览排序确定
            long seedDate = 1_699_000_000_000L + i * 1000;
            addDoc(ops, 1000 + i, "Other Product " + i, "", 99L, "其他",
                    999L, "Generic", null, "ON_SALE", 100 + i * 100, 100 + i * 100,
                    seedDate, seedDate);
        }
        client.bulk(b -> b.index(INDEX).operations(ops).refresh(Refresh.True));

        Logger searchLogger = (Logger) LoggerFactory.getLogger("com.ai.mall.search");
        logAppender.start();
        searchLogger.addAppender(logAppender);
    }

    private void addDoc(List<BulkOperation> ops, Long id, String name, String keywords,
                        Long categoryId, String categoryName, Long brandId, String brandName,
                        String image, String status, Long min, Long max, Long publishedAt,
                        Long updatedAt) {
        ops.add(BulkOperation.of(o -> o.index(i -> i.id(String.valueOf(id)).document(
                new SearchProductDocument(id, name, keywords, categoryId, categoryName, brandId,
                        brandName, image, status, min, max, publishedAt, updatedAt)))));
    }

    @AfterAll
    void dropIndex() {
        try {
            client.indices().delete(d -> d.index(INDEX));
        } catch (Exception ignored) {
            // 容器关闭场景忽略
        }
    }

    @Test
    @DisplayName("S3-001/002 keyword 命中名称与品牌关键词，下架商品不返回，名称加权置顶")
    void keyword_relevance_andOnSaleOnly() throws Exception {
        mockMvc.perform(get("/api/mall/search/products").param("keyword", "keyboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].productId").value(1))
                .andExpect(jsonPath("$.data.items[1].productId").value(2));

        // 中文词命中且下架同名商品被剔除
        mockMvc.perform(get("/api/mall/search/products").param("keyword", "机械"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(3));
    }

    @Test
    @DisplayName("S3-003 响应项仅含摘要白名单字段")
    void responseWhitelist() throws Exception {
        var result = mockMvc
                .perform(get("/api/mall/search/products").param("keyword", "keyboard"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"productId\"", "\"productName\"", "\"mainImage\"",
                "\"minPrice\"", "\"maxPrice\"", "\"brandName\"", "\"categoryName\"");
        assertThat(body).doesNotContain("\"keywords\"", "\"status\"", "\"publishedAt\"", "\"updatedAt\"");
    }

    @Test
    @DisplayName("S3-004 105 条在售：默认 20 分页，size 上限 100，page<1 回退 1")
    void pagination() throws Exception {
        mockMvc.perform(get("/api/mall/search/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(105))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items.length()").value(20));

        mockMvc.perform(get("/api/mall/search/products").param("size", "999").param("page", "2"))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items.length()").value(5));

        mockMvc.perform(get("/api/mall/search/products").param("page", "0"))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    @DisplayName("S4-001/002 类目与品牌 term 过滤")
    void categoryAndBrandFilter() throws Exception {
        mockMvc.perform(get("/api/mall/search/products").param("categoryId", "10"))
                .andExpect(jsonPath("$.data.total").value(4));

        mockMvc.perform(get("/api/mall/search/products")
                        .param("categoryId", "10").param("brandId", "100"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[?(@.productId==5)]").doesNotExist());
    }

    @Test
    @DisplayName("S4-003/007 价格区间相交与单侧开边界")
    void priceRangeIntersection() throws Exception {
        mockMvc.perform(get("/api/mall/search/products")
                        .param("minPriceFen", "10000").param("maxPriceFen", "30000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.productId==1)]").exists())
                .andExpect(jsonPath("$.data.items[?(@.productId==3)]").exists())
                .andExpect(jsonPath("$.data.items[?(@.productId==2)]").doesNotExist());

        mockMvc.perform(get("/api/mall/search/products").param("maxPriceFen", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.productId==1)]").doesNotExist())
                .andExpect(jsonPath("$.data.items[?(@.productId==2)]").exists())
                .andExpect(jsonPath("$.data.items[?(@.productId==6)]").exists());
    }

    @Test
    @DisplayName("S4-004/005 价格升降序；S4-008 newest 无 publishedAt 排尾")
    void sorts() throws Exception {
        mockMvc.perform(get("/api/mall/search/products")
                        .param("categoryId", "10").param("sort", "price_asc"))
                .andExpect(jsonPath("$.data.items[0].productId").value(2))
                .andExpect(jsonPath("$.data.items[1].productId").value(6))
                .andExpect(jsonPath("$.data.items[2].productId").value(1))
                .andExpect(jsonPath("$.data.items[3].productId").value(3));

        mockMvc.perform(get("/api/mall/search/products")
                        .param("categoryId", "10").param("sort", "price_desc"))
                .andExpect(jsonPath("$.data.items[0].productId").value(3))
                .andExpect(jsonPath("$.data.items[3].productId").value(2));

        mockMvc.perform(get("/api/mall/search/products")
                        .param("categoryId", "10").param("sort", "newest"))
                .andExpect(jsonPath("$.data.items[0].productId").value(1))
                .andExpect(jsonPath("$.data.items[1].productId").value(2))
                .andExpect(jsonPath("$.data.items[3].productId").value(6));
    }

    @Test
    @DisplayName("S3-009/S4-006 非法参数 400 B0502")
    void invalidParams() throws Exception {
        mockMvc.perform(get("/api/mall/search/products")
                        .param("minPriceFen", "30000").param("maxPriceFen", "10000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0502"));

        mockMvc.perform(get("/api/mall/search/products").param("minPriceFen", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0502"));

        mockMvc.perform(get("/api/mall/search/products")
                        .param("keyword", "k".repeat(65)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0502"));

        // 未知排序值静默回退 DEFAULT，不报错
        mockMvc.perform(get("/api/mall/search/products").param("sort", "hacker"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("S2-004 空结果 200 空页且搜索链路无 ERROR 日志")
    void emptyResult_noErrorLog() throws Exception {
        mockMvc.perform(get("/api/mall/search/products").param("keyword", "qqqxzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());

        List<ILoggingEvent> errors = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR).toList();
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("S2-003 查询别名不存在时适配器抛出 404 异常（由 Advice 归一 503，见单测）")
    void missingIndex_throws() {
        var adapter = new ElasticsearchProductSearchAdapter(client, "mall_products_missing_alias_it");
        assertThatThrownBy(() -> adapter
                .search(SearchQuery.normalized(
                        null, null, null, null, null,
                        SortMode.DEFAULT, 1, 20)))
                .isInstanceOfAny(RuntimeException.class, Exception.class);
    }
}

