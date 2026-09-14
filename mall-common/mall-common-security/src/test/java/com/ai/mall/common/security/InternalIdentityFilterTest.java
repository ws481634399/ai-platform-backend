package com.ai.mall.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * CHG-0015 TC-013：InternalIdentityFilter 三态单测
 * （无头 401 / 错误头 401 / 正确头注入 ROLE_SERVICE），不启动 Spring 上下文。
 */
@DisplayName("内部凭证过滤器")
class InternalIdentityFilterTest {

    private static final String SECRET = "unit-test-secret";

    private InternalIdentityFilter filter;

    @BeforeEach
    void setUp() {
        InternalSecurityProperties properties = new InternalSecurityProperties();
        properties.setSharedSecret(SECRET);
        filter = new InternalIdentityFilter(properties);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("TC-013a 内部路径缺失 X-Internal-Token → 401 INTERNAL_UNAUTHORIZED 且链路中断")
    void missingTokenRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/internal/inventory/lock");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);

        filter.doFilter(request, response, recordingChain(chainInvoked, new AtomicReference<>()));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INTERNAL_UNAUTHORIZED");
        assertThat(chainInvoked.get()).isFalse();
    }

    @Test
    @DisplayName("TC-013b 内部路径错误凭证 → 401 且链路中断")
    void wrongTokenRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/products/skus/1");
        request.addHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, "guessed-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);

        filter.doFilter(request, response, recordingChain(chainInvoked, new AtomicReference<>()));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INTERNAL_UNAUTHORIZED");
        assertThat(chainInvoked.get()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("TC-013c 正确凭证 → 链路中认证主体为 SERVICE/ROLE_SERVICE，出链后上下文清理")
    void validTokenSetsServiceAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/products/skus/1");
        request.addHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);
        AtomicReference<AuthenticatedSubject> subjectInChain = new AtomicReference<>();

        filter.doFilter(request, response, recordingChain(chainInvoked, subjectInChain));

        assertThat(chainInvoked.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(subjectInChain.get()).isNotNull();
        assertThat(subjectInChain.get().subjectType()).isEqualTo(SubjectType.SERVICE);
        assertThat(subjectInChain.get().subjectId()).isEqualTo("SERVICE");
        // 出链后必须清理，避免线程池复用导致身份残留
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("非内部路径无凭证原样透传，不设置认证上下文")
    void nonInternalPathPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mall/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);

        filter.doFilter(request, response, recordingChain(chainInvoked, new AtomicReference<>()));

        assertThat(chainInvoked.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** 记录链路是否执行，并在链路执行瞬间抓取 SecurityContext 中的主体。 */
    private static FilterChain recordingChain(AtomicReference<Boolean> invoked,
                                              AtomicReference<AuthenticatedSubject> subject) {
        return (request, response) -> {
            invoked.set(true);
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedSubject s) {
                subject.set(s);
                assertThat(authentication.getAuthorities())
                        .anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE"));
            }
        };
    }
}
