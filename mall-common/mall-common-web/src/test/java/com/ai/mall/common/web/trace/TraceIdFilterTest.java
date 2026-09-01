package com.ai.mall.common.web.trace;

import com.ai.mall.common.core.trace.TraceContext;
import com.ai.mall.common.web.config.WebFoundationAutoConfiguration;
import com.ai.mall.common.web.testsupport.TestApplication;
import com.ai.mall.common.web.testsupport.TestEchoController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TraceIdFilter 切片测试：生成、合法透传、非法重生成、MDC 写入、上下文清理（AC-10）。
 */
@WebMvcTest(controllers = TestEchoController.class)
@ContextConfiguration(classes = {TestApplication.class, TestEchoController.class})
@Import(WebFoundationAutoConfiguration.class)
class TraceIdFilterTest {

    private static final String VALID_TRACE_ID = "0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldGenerateTraceIdWhenHeaderMissing() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("pong"))
                .andReturn();
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(headerTraceId).matches("^[0-9a-f]{32}$");
        // 响应体 traceId 与响应头一致
        assertThat(result.getResponse().getContentAsString()).contains(headerTraceId);
    }

    @Test
    void shouldPropagateValidTraceIdWithoutRegeneration() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/ok").header("X-Trace-Id", VALID_TRACE_ID))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getHeader("X-Trace-Id")).isEqualTo(VALID_TRACE_ID);
    }

    @Test
    void shouldRegenerateWhenHeaderInvalid() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/ok").header("X-Trace-Id", "not-valid-trace-id"))
                .andExpect(status().isOk())
                .andReturn();
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(headerTraceId).isNotEqualTo("not-valid-trace-id").matches("^[0-9a-f]{32}$");
    }

    @Test
    void shouldWriteTraceIdIntoMdcDuringRequest() throws Exception {
        // /test/mdc 返回请求处理期间的 MDC traceId——即日志行将打印的值
        MvcResult result = mockMvc.perform(get("/test/mdc"))
                .andExpect(status().isOk())
                .andReturn();
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(result.getResponse().getContentAsString()).contains(headerTraceId);
    }

    @Test
    void shouldCleanupContextAfterRequest() throws Exception {
        mockMvc.perform(get("/test/ok")).andExpect(status().isOk());
        // 请求结束后上下文必须清空（防线程池复用污染）
        assertThat(TraceContext.get()).isNull();
        // 二次请求应重新生成而非复用
        String first = mockMvc.perform(get("/test/ok")).andReturn().getResponse().getHeader("X-Trace-Id");
        String second = mockMvc.perform(get("/test/ok")).andReturn().getResponse().getHeader("X-Trace-Id");
        assertThat(first).isNotEqualTo(second);
    }
}
