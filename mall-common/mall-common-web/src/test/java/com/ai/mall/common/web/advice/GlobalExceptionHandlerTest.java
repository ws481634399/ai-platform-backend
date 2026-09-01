package com.ai.mall.common.web.advice;

import com.ai.mall.common.web.config.WebFoundationAutoConfiguration;
import com.ai.mall.common.web.testsupport.TestApplication;
import com.ai.mall.common.web.testsupport.TestEchoController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler 切片测试：参数/业务/系统三类异常转换与信息不泄露（AC-10）。
 */
@WebMvcTest(controllers = TestEchoController.class)
@ContextConfiguration(classes = {TestApplication.class, TestEchoController.class})
@Import(WebFoundationAutoConfiguration.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void paramViolationShouldReturn400WithACode() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value(containsString("name")));
    }

    @Test
    void businessExceptionShouldReturnDefault400WithBCode() throws Exception {
        mockMvc.perform(get("/test/biz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("B0001"))
                .andExpect(jsonPath("$.message").value("库存不足"));
    }

    @Test
    void businessExceptionShouldCarryHttpStatusSemantics() throws Exception {
        mockMvc.perform(get("/test/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("B0001"))
                .andExpect(jsonPath("$.message").value("商品不存在"));
    }

    @Test
    void unexpectedExceptionShouldReturn500WithGenericMessage() throws Exception {
        String body = mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("S0001"))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"))
                .andReturn().getResponse().getContentAsString();
        // 响应体不泄露内部信息：无异常消息、无 SQL、无类名/堆栈痕迹
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("select")
                .doesNotContain("IllegalStateException")
                .doesNotContain("jdk internal");
    }

    @Test
    void allResponsesShouldCarryTraceId() throws Exception {
        mockMvc.perform(get("/test/biz"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void responseShouldNotExposeStackTrace() throws Exception {
        String body = mockMvc.perform(get("/test/boom")).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("at com.ai.mall")
                .doesNotContain("at org.springframework");
    }
}
