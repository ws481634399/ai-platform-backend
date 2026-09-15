package com.ai.mall.identity.application.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.identity.application.port.MemberProvisioner;
import com.ai.mall.identity.support.ApiTestSecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 会员登录/刷新/退出接口集成测试（CHG-0016 / STORY-003-01-01-02 / TC-001~TC-006）。
 *
 * <p>走真实安全链（ApiTestSecurityConfig 进程内 RSA），覆盖：登录双令牌+JWT claim+cookie、
 * 凭据错误统一 401、禁用 403、refresh family 旋转与重放整族撤销、logout 撤族+auth_version+1。
 * 所有库断言均按本用例唯一用户名过滤（共享 H2 库，禁止无条件 COUNT）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("STORY-003-01-01-02 会员登录与会话 API")
class MemberSessionApiTest {

    private static final String PASSWORD = "Abcd1234";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtDecoder jwtDecoder;
    @MockitoBean MemberProvisioner provisioner;

    @Test
    @DisplayName("TC-001 登录成功 → 200 双令牌在响应体+memberId 字符串+HttpOnly cookie；JWT claim sub=memberId/subject_type=MEMBER/auth_version")
    void loginIssuesTokenPairAndMemberClaims() throws Exception {
        register("login_alice_2");
        MvcResult result = mockMvc.perform(post("/api/auth/member/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login_alice_2", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.accessExpiresAt").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.memberId").exists())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("refresh_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Path=/api/auth/member")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("SameSite=Strict")))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.get("memberId").isTextual()).isTrue();
        String memberId = data.get("memberId").asText();

        Jwt jwt = jwtDecoder.decode(data.get("accessToken").asText());
        assertThat(jwt.getSubject()).isEqualTo(memberId);
        assertThat(jwt.getClaimAsString("subject_type")).isEqualTo("MEMBER");
        assertThat(jwt.getClaimAsString("username")).isEqualTo("login_alice_2");
        assertThat((Object) jwt.getClaim("auth_version")).isEqualTo(1L);
    }

    @Test
    @DisplayName("TC-002 错密码/不存在用户 → 同为 401「用户名或密码错误」")
    void invalidCredentialsUnifiedMessage() throws Exception {
        register("login_badpw_2");
        mockMvc.perform(post("/api/auth/member/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login_badpw_2", "WrongPass99")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        mockMvc.perform(post("/api/auth/member/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login_ghost_2", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("TC-003 DISABLED 账号密码正确 → 403「账号已禁用」")
    void disabledAccountRejectedWith403() throws Exception {
        register("login_frozen_2");
        jdbc.update("UPDATE member_user SET status = 'DISABLED' WHERE username_norm = 'login_frozen_2'");

        mockMvc.perform(post("/api/auth/member/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login_frozen_2", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("账号已禁用"));
    }

    @Test
    @DisplayName("TC-004/TC-005 refresh 旋转：新双令牌；旧令牌重放 → 401 且整族撤销（新令牌也失效）")
    void refreshRotatesAndReplayRevokesFamily() throws Exception {
        register("rot_replay_2");
        JsonNode first = login("rot_replay_2");
        String r1 = first.get("refreshToken").asText();
        String a1 = first.get("accessToken").asText();

        MvcResult rotated = mockMvc.perform(post("/api/auth/member/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + r1 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();
        JsonNode second = objectMapper.readTree(rotated.getResponse().getContentAsString()).get("data");
        String r2 = second.get("refreshToken").asText();
        String a2 = second.get("accessToken").asText();
        assertThat(r2).isNotEqualTo(r1);
        assertThat(a2).isNotEqualTo(a1);
        // 旋转后 access 携带当前 auth_version=1 且可验签
        assertThat(jwtDecoder.decode(a2).getSubject()).isEqualTo(first.get("memberId").asText());

        // 旧 refresh 重放 → 401，触发整族撤销
        mockMvc.perform(post("/api/auth/member/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + r1 + "\"}"))
                .andExpect(status().isUnauthorized());

        // 整族已撤销：旋转后的 r2 同样 401
        mockMvc.perform(post("/api/auth/member/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + r2 + "\"}"))
                .andExpect(status().isUnauthorized());

        // 库里该 family 所有行均已撤销（按唯一用户名 JOIN 过滤）
        Integer revoked = jdbc.queryForObject("""
                SELECT COUNT(*) FROM member_refresh_token r
                JOIN member_user u ON r.member_id = u.id
                WHERE u.username_norm = 'rot_replay_2' AND r.revoked_at IS NOT NULL
                """, Integer.class);
        Integer total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM member_refresh_token r
                JOIN member_user u ON r.member_id = u.id
                WHERE u.username_norm = 'rot_replay_2'
                """, Integer.class);
        assertThat(revoked).isEqualTo(total).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("TC-004 伪造/缺失 refresh → 401")
    void invalidRefreshTokenRejected() throws Exception {
        mockMvc.perform(post("/api/auth/member/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-a-real-token\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/member/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-006 logout（MEMBER）→ 204 清 cookie；全部 refresh 撤销 + auth_version+1；之后 refresh 401")
    void logoutRevokesFamilyAndBumpsAuthVersion() throws Exception {
        register("logout_bob_2");
        JsonNode pair = login("logout_bob_2");
        String access = pair.get("accessToken").asText();
        String refresh = pair.get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/member/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("refresh_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        assertThat(jdbc.queryForObject(
                "SELECT auth_version FROM member_user WHERE username_norm = 'logout_bob_2'", Long.class))
                .isEqualTo(2L);
        Integer revoked = jdbc.queryForObject("""
                SELECT COUNT(*) FROM member_refresh_token r
                JOIN member_user u ON r.member_id = u.id
                WHERE u.username_norm = 'logout_bob_2' AND r.revoked_at IS NOT NULL
                """, Integer.class);
        assertThat(revoked).isEqualTo(1);

        // 撤销 + auth_version 不匹配：refresh 必 401
        mockMvc.perform(post("/api/auth/member/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-007/TC-008 identity 安全链：MEMBER JWT 访问 /api/admin/** → 403；匿名 logout → 401")
    void memberTokenCannotReachAdminScopeAndLogoutRequiresAuth() throws Exception {
        register("scope_check_2");
        String access = login("scope_check_2").get("accessToken").asText();

        mockMvc.perform(post("/api/admin/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/member/logout"))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/auth/member/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/member/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private static String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}
