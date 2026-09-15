package com.ai.mall.identity.interfaces.rest.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.identity.application.member.MemberRegistrationService;
import com.ai.mall.identity.application.port.MemberProvisioner;
import com.ai.mall.identity.support.ApiTestSecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * identity profile-seed 内部端点测试（CHG-0016 / TC-006 懒补偿种子）。
 * 内部路径只认 X-Internal-Token：无凭证/错凭证/ADMIN JWT 一律 401。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("STORY-003-01-01-01 profile-seed 内部端点")
class InternalMemberSeedApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRegistrationService registration;
    @Autowired JwtEncoder encoder;
    @MockitoBean MemberProvisioner provisioner;

    @Value("${mall.security.internal.shared-secret}")
    String sharedSecret;

    @Test
    @DisplayName("无凭证 / 错误凭证 / ADMIN JWT 访问 → 401 INTERNAL_UNAUTHORIZED")
    void unauthenticatedOrJwtRejected() throws Exception {
        mockMvc.perform(get("/api/internal/members/1/profile-seed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_UNAUTHORIZED"));

        mockMvc.perform(get("/api/internal/members/1/profile-seed")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_UNAUTHORIZED"));

        mockMvc.perform(get("/api/internal/members/1/profile-seed")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("正确凭证 + 账号不存在 → 404")
    void unknownMemberNotFound() throws Exception {
        mockMvc.perform(get("/api/internal/members/999999/profile-seed")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("会员不存在"));
    }

    @Test
    @DisplayName("正确凭证 + 已注册会员 → 200 种子视图（memberId 字符串、status ENABLED）")
    void registeredMemberSeedReturned() throws Exception {
        long memberId = registration.register("Seed_User", "Abcd1234");

        mockMvc.perform(get("/api/internal/members/{id}/profile-seed", memberId)
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(Long.toString(memberId)))
                .andExpect(jsonPath("$.data.username").value("Seed_User"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"));
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
