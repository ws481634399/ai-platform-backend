package com.ai.mall.search.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.ai.mall.search.application.index.RebuildService;
import com.ai.mall.search.application.index.projection.ProductProjectionView;
import com.ai.mall.search.application.index.projection.ProjectionPage;
import com.ai.mall.search.domain.index.SearchIndexPort;
import com.ai.mall.search.infrastructure.client.ProductProjectionClient;
import com.ai.mall.search.infrastructure.elasticsearch.SearchIndexLifecycleManager;
import com.ai.mall.search.support.AbstractElasticsearchTest;
import com.ai.mall.search.support.SearchApiTestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 索引同步/重建/一致性集成测试（CHG-0021）：真实 ES 8.17.4 + H2 Flyway + 真实安全链。
 *
 * <p>覆盖：IK 回退建索引（容器无 IK 插件）、幂等保障、版本化 upsert/乱序消化、
 * 下架删除、硬删端点、全量重建别名原子切换、一致性差集、内部/管理端鉴权。
 */
@SpringBootTest(properties = "mall.search.sync-retry-delay-ms=3600000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SearchApiTestSecurityConfig.class)
@DisplayName("索引同步与重建集成")
class IndexSyncIntegrationTest extends AbstractElasticsearchTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ElasticsearchClient esClient;
    @Autowired SearchIndexPort searchIndexPort;
    @Autowired SearchIndexLifecycleManager lifecycleManager;
    @Autowired RebuildService rebuildService;

    @MockitoBean ProductProjectionClient projectionClient;

    @Value("${mall.security.internal.shared-secret:dev-internal-secret}")
    String internalToken;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("DELETE FROM search_sync_failure_record");
        jdbc.execute("DELETE FROM search_index_rebuild_task");
        // 清空全部 mall_products* 物理索引（含上一用例重建残留），重建幂等保障基线。
        // ES 8 默认禁止通配符删除：先解析为显式索引名再删（无匹配时 GET 404 忽略）。
        try {
            var existing = esClient.indices()
                    .get(g -> g.index("mall_products_v1", "mall_products_rebuild_*")).result().keySet();
            if (!existing.isEmpty()) {
                esClient.indices().delete(d -> d.index(new java.util.ArrayList<>(existing)));
            }
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException ex) {
            if (ex.status() != 404) {
                throw ex;
            }
        }
        lifecycleManager.ensureIndex();
    }

    @Test
    @DisplayName("启动保障：v1 不存在时以 standard 回退映射建索引并挂别名，重复执行幂等")
    void ensureIndexIdempotentWithStandardFallback() {
        assertThat(searchIndexPort.physicalIndicesOf("mall_products")).containsExactly("mall_products_v1");
        lifecycleManager.ensureIndex();
        lifecycleManager.ensureIndex();
        assertThat(searchIndexPort.indexExists("mall_products_v1")).isTrue();
        assertThat(searchIndexPort.physicalIndicesOf("mall_products")).containsExactly("mall_products_v1");
    }

    @Test
    @DisplayName("内部同步 upsert 受理 200，文档可经商城搜索查到")
    void syncUpsertAcceptedAndSearchable() throws Exception {
        ProductProjectionView view = onSaleView(7001L, "同步手机Alpha", 1_700_000_000_000L);

        mockMvc.perform(post("/api/internal/search/products/sync")
                        .header("X-Internal-Token", internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(view)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(true));

        mockMvc.perform(get("/api/mall/search/products").param("keyword", "同步手机Alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value("7001"))
                .andExpect(jsonPath("$.data.items[0].brandName").value("索隐品牌"));
    }

    @Test
    @DisplayName("乱序事件：旧版本 upsert 被 external_gte 消化，新版本内容不被覆盖")
    void staleUpsertSwallowed() throws Exception {
        sync(onSaleView(7002L, "新版本手机", 2_000L));
        sync(onSaleView(7002L, "旧版本手机", 1_000L));

        mockMvc.perform(get("/api/mall/search/products").param("keyword", "版本手机"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].productName").value("新版本手机"));
    }

    @Test
    @DisplayName("非在架投影同步 → 版本化删除文档；版本更旧不删新文档")
    void offSaleSyncDeletesAndStaleDeleteKeepsNew() throws Exception {
        sync(onSaleView(7003L, "待下架手机", 1_000L));
        // 旧版本删除事件不能删掉新文档
        sync(viewWithStatus(7003L, "OFF_SALE", "待下架手机", 999L));
        mockMvc.perform(get("/api/mall/search/products").param("keyword", "待下架手机"))
                .andExpect(jsonPath("$.data.total").value(1));
        // 新版本下架事件删除
        sync(viewWithStatus(7003L, "OFF_SALE", "待下架手机", 2_000L));
        mockMvc.perform(get("/api/mall/search/products").param("keyword", "待下架手机"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("DELETE 内部端点硬删文档，重复删除幂等 200")
    void deleteEndpointIdempotent() throws Exception {
        sync(onSaleView(7004L, "待硬删手机", 1_000L));
        mockMvc.perform(delete("/api/internal/search/products/7004").header("X-Internal-Token", internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));
        mockMvc.perform(get("/api/mall/search/products").param("keyword", "待硬删手机"))
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(delete("/api/internal/search/products/7004").header("X-Internal-Token", internalToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("全量重建：临时索引写入后原子切换别名，旧索引清除，数据可查")
    void fullRebuildSwitchesAliasAtomically() throws Exception {
        sync(onSaleView(7001L, "重建前旧手机", 1_000L));
        List<ProductProjectionView> rebuilt = List.of(
                onSaleView(8001L, "重建商品壹", 2_000L),
                onSaleView(8002L, "重建商品贰", 2_000L),
                onSaleView(8003L, "重建商品叁", 2_000L));
        when(projectionClient.fetchPage(anyInt(), anyInt()))
                .thenReturn(new ProjectionPage(rebuilt, rebuilt.size(), 1, 500))
                .thenReturn(new ProjectionPage(List.of(), rebuilt.size(), 2, 500));

        mockMvc.perform(post("/api/admin/search/index/rebuild")
                        .header("Authorization", "Bearer " + adminToken(List.of("search:index:rebuild"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.indexedCount").value(3))
                .andExpect(jsonPath("$.data.physicalIndex").value(org.hamcrest.Matchers.startsWith("mall_products_rebuild_")));

        var aliasIndices = searchIndexPort.physicalIndicesOf("mall_products");
        assertThat(aliasIndices).hasSize(1);
        assertThat(aliasIndices.iterator().next()).startsWith("mall_products_rebuild_");
        assertThat(searchIndexPort.indexExists("mall_products_v1")).isFalse();
        assertThat(searchIndexPort.count("mall_products")).isEqualTo(3);
        mockMvc.perform(get("/api/mall/search/products").param("keyword", "重建商品"))
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    @DisplayName("一致性检查：product 2 条 / ES 3 条 → extra 差集精确，counts 不受截断影响")
    void consistencyCheckDiffs() throws Exception {
        sync(onSaleView(9001L, "一致性手机甲", 1_000L));
        sync(onSaleView(9002L, "一致性手机乙", 1_000L));
        sync(onSaleView(9003L, "一致性手机丙", 1_000L));
        List<ProductProjectionView> productSide = List.of(
                onSaleView(9001L, "一致性手机甲", 1_000L),
                onSaleView(9002L, "一致性手机乙", 1_000L));
        when(projectionClient.fetchPage(anyInt(), anyInt()))
                .thenReturn(new ProjectionPage(productSide, 2, 1, 500))
                .thenReturn(new ProjectionPage(List.of(), 2, 2, 500));

        mockMvc.perform(get("/api/admin/search/index/consistency-check")
                        .header("Authorization", "Bearer " + adminToken(List.of("search:index:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productOnSaleCount").value(2))
                .andExpect(jsonPath("$.data.indexCount").value(3))
                .andExpect(jsonPath("$.data.missingProductIds.length()").value(0))
                .andExpect(jsonPath("$.data.extraProductIds.length()").value(1))
                .andExpect(jsonPath("$.data.extraProductIds[0]").value(9003))
                .andExpect(jsonPath("$.data.checkedAt").exists());
    }

    @Test
    @DisplayName("鉴权：内部端点无凭证 401；管理端重建无 JWT 401；缺权限码 403")
    void authGuards() throws Exception {
        mockMvc.perform(post("/api/internal/search/products/sync")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/search/index/rebuild"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/search/index/rebuild")
                        .header("Authorization", "Bearer " + adminToken(List.of("search:index:list"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("重建 409：存在 RUNNING 任务时再触发返回 B0503")
    void rebuildConflictWhenRunning() {
        // 直接在库内构造 RUNNING 任务，验证闸门
        jdbc.update("INSERT INTO search_index_rebuild_task(task_no,status,total_count,indexed_count,"
                + "failed_count,physical_index,started_at,created_at,updated_at) "
                + "VALUES('RBL-RUNNING','RUNNING',0,0,0,'mall_products_rebuild_running',NOW(),NOW(),NOW())");
        var ex = org.assertj.core.api.Assertions.catchThrowable(() -> rebuildService.startRebuild());
        assertThat(ex).isInstanceOf(com.ai.mall.common.web.exception.BusinessException.class);
        assertThat(((com.ai.mall.common.web.exception.BusinessException) ex).getErrorCode().getCode())
                .isEqualTo("B0503");
    }

    private void sync(ProductProjectionView view) throws Exception {
        mockMvc.perform(post("/api/internal/search/products/sync")
                        .header("X-Internal-Token", internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(view)))
                .andExpect(status().isOk());
    }

    private String adminToken(List<String> permissions) {
        var claims = JwtClaimsSet.builder()
                .subject("2001").claim("username", "search_admin")
                .claim("subject_type", "ADMIN").claim("auth_version", 1)
                .audience(List.of("mall-admin-api")).issuer("ai-platform")
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(600));
        claims.claim("permissions", permissions);
        return encoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }

    private static ProductProjectionView onSaleView(Long id, String name, Long version) {
        return viewWithStatus(id, "ON_SALE", name, version);
    }

    private static ProductProjectionView viewWithStatus(Long id, String status, String name, Long version) {
        return new ProductProjectionView(
                String.valueOf(id), name, "", 300L, "手机分类", 400L, "索隐品牌",
                "https://cdn.example.com/" + id + ".png", status,
                199_00L, 499_00L, version, version);
    }
}
