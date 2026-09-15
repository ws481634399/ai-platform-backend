package com.ai.mall.member.interfaces.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.member.support.ApiTestSecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * provision 内部接口安全与双幂等 HTTP 测试（CHG-0016 / TC-005、TC-009 配合迁移）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("STORY-003-01-01-01 /api/internal/members/provision")
class InternalMemberProvisionApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;

    @Value("${mall.security.internal.shared-secret}")
    String sharedSecret;

    private static final String BODY =
            "{\"eventId\":\"evt-http-1\",\"memberId\":\"71005555\",\"username\":\"Prov_User\","
                    + "\"nickname\":\"会员005555\",\"occurredAt\":\"2026-09-15T10:00:00Z\"}";

    @Test
    @DisplayName("无凭证 / 错误凭证 / 仅持 ADMIN JWT → 401 INTERNAL_UNAUTHORIZED")
    void credentialRequired() throws Exception {
        mockMvc.perform(post("/api/internal/members/provision")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_UNAUTHORIZED"));

        mockMvc.perform(post("/api/internal/members/provision")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, "nope")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_UNAUTHORIZED"));

        mockMvc.perform(post("/api/internal/members/provision")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-005 正确凭证首次 provisioned=true；同 eventId 重放 200+false；仅一行 profile")
    void provisionThenReplay() throws Exception {
        mockMvc.perform(post("/api/internal/members/provision")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.provisioned").value(true));

        mockMvc.perform(post("/api/internal/members/provision")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provisioned").value(false));

        assertThatOneRowWithDefaults();
    }

    @Test
    @DisplayName("nickname 缺省时服务端兜底默认昵称；非法载荷 → 400 字段级提示")
    void defaultNicknameAndValidation() throws Exception {
        String noNickname = "{\"eventId\":\"evt-http-2\",\"memberId\":\"71006666\","
                + "\"username\":\"Prov_User2\",\"occurredAt\":\"2026-09-15T10:00:00Z\"}";
        mockMvc.perform(post("/api/internal/members/provision")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                        .contentType(MediaType.APPLICATION_JSON).content(noNickname))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provisioned").value(true));
        assertThat(jdbc.queryForObject(
                "SELECT nickname FROM member_profile WHERE member_id = 71006666", String.class))
                .isEqualTo("会员006666");

        String invalid = "{\"eventId\":\"\",\"memberId\":0,\"username\":\"\","
                + "\"occurredAt\":\"2026-09-15T10:00:00Z\"}";
        mockMvc.perform(post("/api/internal/members/provision")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("eventId")));
    }

    private void assertThatOneRowWithDefaults() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_profile WHERE member_id = 71005555", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT nickname FROM member_profile WHERE member_id = 71005555", String.class))
                .isEqualTo("会员005555");
        assertThat(jdbc.queryForObject(
                "SELECT gender FROM member_profile WHERE member_id = 71005555", String.class))
                .isEqualTo("UNKNOWN");
    }

    private String adminToken() {
        var claims = JwtClaimsSet.builder()
                .subject("1").claim("username", "boss").claim("subject_type", "ADMIN")
                .claim("auth_version", 1).audience(List.of(ApiTestSecurityConfig.AUDIENCE))
                .issuer(ApiTestSecurityConfig.ISSUER)
                .issuedAt(Instant.now().minusSeconds(5)).expiresAt(Instant.now().plusSeconds(600))
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
