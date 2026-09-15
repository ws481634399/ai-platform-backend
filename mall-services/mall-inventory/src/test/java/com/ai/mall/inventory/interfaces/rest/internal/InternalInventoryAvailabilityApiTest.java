package com.ai.mall.inventory.interfaces.rest.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.inventory.support.ApiTestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 库存内部可售查询 API 集成测试：CHG-0017 STORY-003-02-03-01 TC-001/TC-006。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("库存内部可售查询 API")
class InternalInventoryAvailabilityApiTest {

    private static final String INTERNAL_TOKEN = "dev-internal-secret";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        jdbc.execute("DELETE FROM inventory_log");
        jdbc.execute("DELETE FROM inventory_stock");
    }

    private String body(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @Test
    @DisplayName("TC-001: 批量查询精确数量，无记录 skuId=0；按请求顺序返回")
    void batchAvailableQty() throws Exception {
        jdbc.update("INSERT INTO inventory_stock(id, sku_id, total_quantity, locked_quantity, "
                + "created_at, updated_at, version) VALUES (1, 101, 100, 0, NOW(), NOW(), 0)");
        jdbc.update("INSERT INTO inventory_stock(id, sku_id, total_quantity, locked_quantity, "
                + "created_at, updated_at, version) VALUES (2, 102, 50, 10, NOW(), NOW(), 0)");
        // sku 103 无记录 → 0

        mockMvc.perform(post("/api/internal/inventory/availability")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("skuIds", List.of(101L, 102L, 103L)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].skuId").value("101"))
                .andExpect(jsonPath("$.data[0].availableQty").value(100))
                .andExpect(jsonPath("$.data[1].skuId").value("102"))
                .andExpect(jsonPath("$.data[1].availableQty").value(40))
                .andExpect(jsonPath("$.data[2].skuId").value("103"))
                .andExpect(jsonPath("$.data[2].availableQty").value(0));
    }

    @Test
    @DisplayName("TC-006: 无 X-Internal-Token → 401")
    void noTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/internal/inventory/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("skuIds", List.of(101L)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-006: 空/超 100/非正 → 400")
    void batchValidation() throws Exception {
        mockMvc.perform(post("/api/internal/inventory/availability")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("skuIds", List.of()))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/internal/inventory/availability")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("skuIds", List.of(-1L)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("超 100 条 → 400")
    void batchOver100() throws Exception {
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();
        mockMvc.perform(post("/api/internal/inventory/availability")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("skuIds", ids))))
                .andExpect(status().isBadRequest());
    }
}
