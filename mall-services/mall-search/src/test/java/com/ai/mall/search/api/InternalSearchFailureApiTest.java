package com.ai.mall.search.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.search.domain.index.SearchSyncFailure;
import com.ai.mall.search.domain.index.SyncEventType;
import com.ai.mall.search.domain.index.SyncFailureRepository;
import com.ai.mall.search.support.SearchApiTestSecurityConfig;
import java.time.Instant;
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
 * 内部失败记录只读端点测试（CHG-0021 DU-BE-506 评审补点）：
 * GET /api/internal/search/sync-failures（requirement-design §2.4，ROLE_SERVICE）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SearchApiTestSecurityConfig.class)
@DisplayName("内部同步失败只读端点")
class InternalSearchFailureApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SyncFailureRepository repository;

    @Value("${mall.security.internal.shared-secret:dev-internal-secret}")
    String internalToken;

    @BeforeEach
    void reset() {
        jdbc.execute("DELETE FROM search_sync_failure_record");
    }

    @Test
    @DisplayName("SERVICE 令牌：分页返回失败记录，status 筛选 FAILED_DEAD")
    void listWithServiceTokenAndStatusFilter() throws Exception {
        SearchSyncFailure pending = SearchSyncFailure.register(8001L, SyncEventType.UPSERT, "down", Instant.now());
        repository.insert(pending);
        SearchSyncFailure dead = SearchSyncFailure.register(8002L, SyncEventType.DELETE, "down again", Instant.now());
        repository.insert(dead);
        dead.recordRetryFailure("x1", Instant.now());
        dead.recordRetryFailure("x2", Instant.now());
        dead.recordRetryFailure("x3", Instant.now());
        dead.recordRetryFailure("x4", Instant.now());
        dead.recordRetryFailure("x5", Instant.now());
        repository.update(dead);

        mockMvc.perform(get("/api/internal/search/sync-failures").header("X-Internal-Token", internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        mockMvc.perform(get("/api/internal/search/sync-failures")
                        .header("X-Internal-Token", internalToken)
                        .param("status", "FAILED_DEAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(8002))
                .andExpect(jsonPath("$.data.items[0].status").value("FAILED_DEAD"));
    }

    @Test
    @DisplayName("无内部令牌拒绝访问（4xx）")
    void rejectedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/internal/search/sync-failures"))
                .andExpect(status().is4xxClientError());
    }
}
