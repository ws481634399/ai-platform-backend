package com.ai.mall.common.security;

import com.ai.mall.common.core.result.ErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * 服务间内部接口身份过滤器（CHG-0015）：仅匹配配置路径（默认 {@code /api/internal/**}），
 * 校验共享凭证请求头 {@code X-Internal-Token}：
 * <ul>
 *   <li>凭证缺失或不匹配：直接写 401 {@link UnifyResult} 错误体（code=INTERNAL_UNAUTHORIZED）并中断链路，
 *       即使携带合法 JWT 也不予放行——内部调用只认共享凭证；</li>
 *   <li>凭证匹配：设置 principal={@link AuthenticatedSubject}（subjectType=SERVICE）、
 *       authority=ROLE_SERVICE 的 Authentication，供授权层 {@code hasRole('SERVICE')} 判定；</li>
 *   <li>非内部路径：原样透传，不触碰安全上下文。</li>
 * </ul>
 *
 * <p>必须被加入各 servlet 服务的 SecurityFilterChain（AuthorizationFilter 之前），
 * 不作为独立 servlet Filter 在外层注册（由自动装配禁用 Boot 默认注册）。
 */
public class InternalIdentityFilter extends OncePerRequestFilter {

    /** 服务间共享凭证请求头（SSOT，requirement-design.md §5.1）。 */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /** 内部调用统一主体标识。 */
    public static final String SERVICE_SUBJECT_ID = "SERVICE";

    static final ErrorCode INTERNAL_UNAUTHORIZED = new ErrorCode() {
        @Override
        public String getCode() {
            return "INTERNAL_UNAUTHORIZED";
        }

        @Override
        public String getMessage() {
            return "内部接口凭证缺失或无效";
        }
    };

    private final InternalSecurityProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalIdentityFilter(InternalSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = urlPathHelper.getPathWithinApplication(request);
        if (!pathMatcher.match(properties.getPath(), path)) {
            // 非内部路径：不介入认证，交由安全链其余过滤器处理
            filterChain.doFilter(request, response);
            return;
        }

        String presented = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (presented == null || !constantTimeEquals(presented, properties.getSharedSecret())) {
            writeUnauthorized(response);
            return;
        }

        AuthenticatedSubject subject = new AuthenticatedSubject(
                SERVICE_SUBJECT_ID, "", SubjectType.SERVICE, 0L, Set.of());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                subject, presented, Set.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 内部凭证认证仅在本次请求内有效，出链即清理，避免线程复用残留
            SecurityContextHolder.clearContext();
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                UnifyResult.fail(INTERNAL_UNAUTHORIZED, INTERNAL_UNAUTHORIZED.getMessage()));
    }

    /** 常量时间比较，避免凭证校验被时序侧信道利用。 */
    private static boolean constantTimeEquals(String presented, String expected) {
        if (expected == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
