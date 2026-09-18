package com.ai.mall.search.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.common.config.FeatureDisabledException;
import com.ai.mall.common.config.FeatureGate;
import com.ai.mall.search.support.AbstractElasticsearchTest;
import com.ai.mall.search.support.SearchApiTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CHG-0022 Story3 商品搜索开关切点测试（S3-TC-001/002）：
 * search.enabled 显式关闭 → 匿名搜索 403 B0606；开启 → 200（空结果也放行）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SearchApiTestSecurityConfig.class)
@DisplayName("商品搜索功能开关切点")
class SearchFeatureGateTest extends AbstractElasticsearchTest {

    private static final String FEATURE = "search.enabled";

    @Autowired MockMvc mockMvc;
    @MockitoBean FeatureGate featureGate;

    @Test
    @DisplayName("S3-TC-001：搜索关闭 → 403 FEATURE_DISABLED(B0606)，不进入 ES 查询")
    void searchDisabledReturns403() throws Exception {
        doThrow(new FeatureDisabledException(FEATURE))
                .when(featureGate).ensureEnabled(eq(FEATURE));

        mockMvc.perform(get("/api/mall/search/products").param("keyword", "机械键盘"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("B0606"));
    }

    @Test
    @DisplayName("S3-TC-002：搜索开启（mock 放行）→ 200")
    void searchEnabledPasses() throws Exception {
        doNothing().when(featureGate).ensureEnabled(eq(FEATURE));

        mockMvc.perform(get("/api/mall/search/products").param("keyword", "机械键盘"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
