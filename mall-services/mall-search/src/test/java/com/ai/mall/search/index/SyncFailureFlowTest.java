package com.ai.mall.search.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.search.application.index.SearchSyncFailureService;
import com.ai.mall.search.application.index.SyncReceiveService;
import com.ai.mall.search.application.index.projection.ProductProjectionView;
import com.ai.mall.search.domain.index.SearchSyncFailure;
import com.ai.mall.search.domain.index.SyncEventType;
import com.ai.mall.search.domain.index.SyncFailureStatus;
import com.ai.mall.search.domain.index.SyncFailureRepository;
import com.ai.mall.search.domain.index.SearchIndexPort;
import com.ai.mall.search.domain.index.SearchIndexPort.IndexWriteResult;
import com.ai.mall.search.infrastructure.client.ProductProjectionClient;
import com.ai.mall.search.support.SearchApiTestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 同步失败退避链路测试（CHG-0021 DU-BE-506）：H2 持久化 + ES 端口/投影客户端全 Mock。
 *
 * <p>覆盖：ES 故障受理 200 落 PENDING、退避窗口 30s/1m/2m/5m/10m、5 次 FAILED_DEAD、
 * 重放重拉当前态成功、STALE_VERSION 按成功消化、人工重试复活、人工重试未知 404（B0504）。
 */
@SpringBootTest(properties = "mall.search.sync-retry-delay-ms=3600000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SearchApiTestSecurityConfig.class)
@DisplayName("同步失败退避重试")
class SyncFailureFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired SyncReceiveService syncReceiveService;
    @Autowired SearchSyncFailureService failureService;
    @Autowired SyncFailureRepository repository;

    @MockitoBean SearchIndexPort searchIndexPort;
    @MockitoBean ProductProjectionClient projectionClient;

    @Value("${mall.security.internal.shared-secret:dev-internal-secret}")
    String internalToken;

    @BeforeEach
    void reset() {
        jdbc.execute("DELETE FROM search_sync_failure_record");
        org.mockito.Mockito.reset(searchIndexPort, projectionClient);
        // 启动保障（SearchIndexLifecycleManager）桩：不触碰真实 ES
        when(searchIndexPort.indexExists(anyString())).thenReturn(false);
        when(searchIndexPort.physicalIndicesOf(anyString())).thenReturn(Set.of());
    }

    @Test
    @DisplayName("ES 故障：内部同步仍 200 受理，落 1 条 PENDING（首次退避 30s）")
    void acceptedOnEsFailureAndRecorded() throws Exception {
        when(searchIndexPort.upsertVersioned(any()))
                .thenThrow(new RuntimeException("simulated es down"));

        mockMvc.perform(post("/api/internal/search/products/sync")
                        .header("X-Internal-Token", internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(view(6001L, 1_000L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));

        List<SearchSyncFailure> pending = repository.page(null, 1, 20);
        assertThat(pending).hasSize(1);
        SearchSyncFailure failure = pending.get(0);
        assertThat(failure.getProductId()).isEqualTo(6001L);
        assertThat(failure.getEventType()).isEqualTo(SyncEventType.UPSERT);
        assertThat(failure.getStatus()).isEqualTo(SyncFailureStatus.PENDING);
        assertThat(failure.getRetryCount()).isZero();
        assertThat(Duration.between(Instant.now(), failure.getNextRetryAt()).getSeconds())
                .isBetween(25L, 30L);
    }

    @Test
    @DisplayName("连续失败退避 30s/1m/2m/5m/10m，第 5 次转 FAILED_DEAD")
    void backoffSequenceAndDead() {
        when(searchIndexPort.upsertVersioned(any())).thenThrow(new RuntimeException("es down"));
        when(projectionClient.fetchOne(anyLong())).thenReturn(view(6002L, 1_000L));

        SearchSyncFailure failure = SearchSyncFailure.register(6002L, SyncEventType.UPSERT, "first", Instant.now());
        repository.insert(failure);
        long[] expectedSeconds = {30, 60, 120, 300, 600};
        for (int i = 0; i < 5; i++) {
            Instant before = Instant.now();
            failureService.replay(repository.findById(failure.getId()).orElseThrow());
            SearchSyncFailure refreshed = repository.findById(failure.getId()).orElseThrow();
            if (i < 4) {
                assertThat(refreshed.getStatus()).isEqualTo(SyncFailureStatus.PENDING);
                long gap = Duration.between(before, refreshed.getNextRetryAt()).getSeconds();
                assertThat(gap).as("第 %d 次退避", i + 1).isBetween(expectedSeconds[i] - 5, expectedSeconds[i]);
            } else {
                assertThat(refreshed.getStatus()).isEqualTo(SyncFailureStatus.FAILED_DEAD);
                assertThat(refreshed.getRetryCount()).isEqualTo(5);
            }
        }
    }

    @Test
    @DisplayName("重放成功：重拉投影 upsert 成功 → SUCCESS；STALE_VERSION 同样按成功")
    void replaySuccessAndStaleAsSuccess() {
        // 场景 1：真实写入成功
        when(searchIndexPort.upsertVersioned(any())).thenReturn(IndexWriteResult.WRITTEN);
        when(projectionClient.fetchOne(6003L)).thenReturn(view(6003L, 2_000L));
        SearchSyncFailure failure = SearchSyncFailure.register(6003L, SyncEventType.UPSERT, "down", Instant.now());
        repository.insert(failure);
        failureService.replay(repository.findById(failure.getId()).orElseThrow());
        assertThat(repository.findById(failure.getId()).orElseThrow().getStatus())
                .isEqualTo(SyncFailureStatus.SUCCESS);

        // 场景 2：重放时更新版本已在（STALE_VERSION），按成功消化
        when(searchIndexPort.upsertVersioned(any())).thenReturn(IndexWriteResult.STALE_VERSION);
        when(projectionClient.fetchOne(6004L)).thenReturn(view(6004L, 3_000L));
        SearchSyncFailure stale = SearchSyncFailure.register(6004L, SyncEventType.UPSERT, "down", Instant.now());
        repository.insert(stale);
        failureService.replay(repository.findById(stale.getId()).orElseThrow());
        assertThat(repository.findById(stale.getId()).orElseThrow().getStatus())
                .isEqualTo(SyncFailureStatus.SUCCESS);
    }

    @Test
    @DisplayName("重放时投影查无（已下架）→ 硬删文档并 SUCCESS")
    void replayDeleteWhenProjectionMissing() {
        when(projectionClient.fetchOne(6005L)).thenReturn(null);
        SearchSyncFailure failure = SearchSyncFailure.register(6005L, SyncEventType.DELETE, "down", Instant.now());
        repository.insert(failure);

        failureService.replay(repository.findById(failure.getId()).orElseThrow());

        org.mockito.Mockito.verify(searchIndexPort).deletePlain(6005L);
        assertThat(repository.findById(failure.getId()).orElseThrow().getStatus())
                .isEqualTo(SyncFailureStatus.SUCCESS);
    }

    @Test
    @DisplayName("人工重试：FAILED_DEAD 复活立即执行成功；未知 id → 404 B0504")
    void manualRetryRearmAndNotFound() {
        when(searchIndexPort.upsertVersioned(any())).thenReturn(IndexWriteResult.WRITTEN);
        when(projectionClient.fetchOne(6006L)).thenReturn(view(6006L, 5_000L));
        SearchSyncFailure dead = SearchSyncFailure.register(6006L, SyncEventType.UPSERT, "down", Instant.now());
        repository.insert(dead);
        dead.recordRetryFailure("x1", Instant.now());
        dead.recordRetryFailure("x2", Instant.now());
        dead.recordRetryFailure("x3", Instant.now());
        dead.recordRetryFailure("x4", Instant.now());
        dead.recordRetryFailure("x5", Instant.now());
        repository.update(dead);
        assertThat(dead.getStatus()).isEqualTo(SyncFailureStatus.FAILED_DEAD);

        SearchSyncFailure retried = failureService.manualRetry(dead.getId());
        assertThat(retried.getStatus()).isEqualTo(SyncFailureStatus.SUCCESS);

        Throwable ex = org.assertj.core.api.Assertions.catchThrowable(() -> failureService.manualRetry(999_999L));
        assertThat(ex).isInstanceOf(BusinessException.class);
        BusinessException business = (BusinessException) ex;
        assertThat(business.getErrorCode().getCode()).isEqualTo("B0504");
        assertThat(business.getHttpStatus().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("同 (productId,eventType) 重复故障复用同一 PENDING 行（不新增）")
    void duplicateFailureReusesRow() {
        when(searchIndexPort.upsertVersioned(any())).thenThrow(new RuntimeException("down"));
        syncReceiveService.receive(view(6007L, 1_000L));
        syncReceiveService.receive(view(6007L, 2_000L));
        List<SearchSyncFailure> rows = repository.page(null, 1, 20);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRetryCount()).isZero();
    }

    private static ProductProjectionView view(Long id, Long version) {
        return new ProductProjectionView(
                String.valueOf(id), "退避手机", "", 300L, "手机分类", 400L, "品牌",
                null, "ON_SALE", 100L, 200L, version, version);
    }
}
