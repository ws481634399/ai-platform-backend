package com.ai.mall.product.interfaces.rest.mall;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.product.support.ApiTestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 商城公开 SKU 可售状态 API 集成测试：CHG-0017 STORY-003-02-03-01。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("商城公开 SKU 可售状态 API")
class MallSkuAvailabilityApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean
    private com.ai.mall.product.infrastructure.client.InventoryAvailabilityClient inventoryClient;

    private String body(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @Test
    @DisplayName("TC-003: 三态映射 0=OUT_OF_STOCK,1-9=LOW_STOCK,≥10=IN_STOCK；无记录=0")
    void threeStateMapping() throws Exception {
        when(inventoryClient.availability(anyList())).thenReturn(Map.of(
                1L, 0L,      // OUT_OF_STOCK
                2L, 5L,      // LOW_STOCK
                3L, 10L,     // IN_STOCK
                4L, 100L));  // IN_STOCK
        // skuId=5 无记录 → 0 → OUT_OF_STOCK
        mockMvc.perform(post("/api/mall/skus/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("skuIds", List.of(1L, 2L, 3L, 4L, 5L)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].skuId").value("1"))
                .andExpect(jsonPath("$.data[0].stockStatus").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.data[1].stockStatus").value("LOW_STOCK"))
                .andExpect(jsonPath("$.data[2].stockStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.data[3].stockStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.data[4].stockStatus").value("OUT_OF_STOCK"));
    }

    @Test
    @DisplayName("TC-004: 响应白名单——仅 skuId+stockStatus，无数量字段")
    void whitelistNoQuantity() throws Exception {
        when(inventoryClient.availability(anyList())).thenReturn(Map.of(1L, 50L));
        mockMvc.perform(post("/api/mall/skus/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("skuIds", List.of(1L)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].skuId").exists())
                .andExpect(jsonPath("$.data[0].stockStatus").exists())
                .andExpect(jsonPath("$.data[0].availableQty").doesNotExist())
                .andExpect(jsonPath("$.data[0].quantity").doesNotExist());
    }

    @Test
    @DisplayName("TC-005: inventory 故障/异常 → 全部 UNKNOWN，HTTP 200")
    void degradationOnInventoryFailure() throws Exception {
        when(inventoryClient.availability(anyList())).thenThrow(new RuntimeException("inventory 503"));
        mockMvc.perform(post("/api/mall/skus/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("skuIds", List.of(1L, 2L)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stockStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data[1].stockStatus").value("UNKNOWN"));
    }

    @Test
    @DisplayName("TC-006: 批量校验——空/超 100/非正 → 400")
    void batchValidation() throws Exception {
        // 空
        mockMvc.perform(post("/api/mall/skus/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("skuIds", List.of()))))
                .andExpect(status().isBadRequest());

        // 负数
        mockMvc.perform(post("/api/mall/skus/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("skuIds", List.of(-1L)))))
                .andExpect(status().isBadRequest());

        // 0
        mockMvc.perform(post("/api/mall/skus/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("skuIds", List.of(0L)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("超 100 条 → 400")
    void batchOver100() throws Exception {
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();
        mockMvc.perform(post("/api/mall/skus/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("skuIds", ids))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("id 字符串输出")
    void idStringOutput() throws Exception {
        when(inventoryClient.availability(anyList())).thenReturn(Map.of(1L, 10L));
        mockMvc.perform(post("/api/mall/skus/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("skuIds", List.of(1L)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].skuId").value("1"));
    }
}
