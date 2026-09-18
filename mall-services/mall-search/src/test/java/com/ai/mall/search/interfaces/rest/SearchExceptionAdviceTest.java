package com.ai.mall.search.interfaces.rest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ai.mall.common.web.trace.TraceIdFilter;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DU-BE-510 异常归一单测：连接/超时/404 → 503 B0501；参数非法 → 400 B0502；
 * WARN 日志带 traceId；响应体不泄漏 ES 主机与堆栈。
 */
class SearchExceptionAdviceTest {

    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        ThrowingController controller = new ThrowingController();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new TraceIdFilter())
                .setControllerAdvice(new SearchExceptionAdvice())
                .build();
        Logger logger = (Logger) LoggerFactory.getLogger(SearchExceptionAdvice.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(SearchExceptionAdvice.class)).detachAppender(appender);
    }

    @Test
    @DisplayName("ES 连接拒绝（cause 链）→ 503 B0501，traceId 回写响应头与 WARN 日志")
    void connectRefused_returns503() throws Exception {
        mockMvc.perform(get("/throw/connect"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("B0501"))
                .andExpect(header().exists("X-Trace-Id"));
        List<ILoggingEvent> warnEvents = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("搜索不可用")).toList();
        assertThat(warnEvents).hasSize(1);
        assertThat(warnEvents.get(0).getMDCPropertyMap()).containsKey("traceId");
    }

    @Test
    @DisplayName("ES 读超时（cause 链）→ 503 B0501")
    void socketTimeout_returns503() throws Exception {
        mockMvc.perform(get("/throw/timeout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("B0501"));
    }

    @Test
    @DisplayName("ES 404（别名不存在）→ 503 B0501，响应体不泄漏内部主机")
    void indexMissing_returns503() throws Exception {
        mockMvc.perform(get("/throw/not-found"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("B0501"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("10.0.0.9").doesNotContain("at "))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("参数非法 → 400 B0502，回显安全文案")
    void illegalArgument_returns400() throws Exception {
        mockMvc.perform(get("/throw/bad-arg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0502"))
                .andExpect(jsonPath("$.message").value("minPriceFen 不能大于 maxPriceFen"));
    }

    /** 专用异常控制器：每个端点制造一类真实异常形态。 */
    @RestController
    static class ThrowingController {

        @GetMapping("/throw/connect")
        public String connect() throws IOException {
            throw new IOException("call failed", new ConnectException("Connection refused: /10.0.0.9:9200"));
        }

        @GetMapping("/throw/timeout")
        public String timeout() throws IOException {
            throw new IOException(new SocketTimeoutException("Read timed out after 5000 MILLISECONDS"));
        }

        @GetMapping("/throw/not-found")
        public String notFound() throws IOException {
            StatusLine statusLine = Mockito.mock(StatusLine.class);
            Mockito.when(statusLine.getStatusCode()).thenReturn(404);
            RequestLine requestLine = Mockito.mock(RequestLine.class);
            Mockito.when(requestLine.getMethod()).thenReturn("GET");
            Mockito.when(requestLine.getUri()).thenReturn("/mall_products/_search");
            Response response = Mockito.mock(Response.class);
            Mockito.when(response.getStatusLine()).thenReturn(statusLine);
            Mockito.when(response.getRequestLine()).thenReturn(requestLine);
            throw new ResponseException(response);
        }

        @GetMapping("/throw/bad-arg")
        public String badArg() {
            throw new IllegalArgumentException("minPriceFen 不能大于 maxPriceFen");
        }
    }
}
