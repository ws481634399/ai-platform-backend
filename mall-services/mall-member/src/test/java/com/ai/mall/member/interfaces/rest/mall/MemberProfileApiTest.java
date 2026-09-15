package com.ai.mall.member.interfaces.rest.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.member.application.member.MemberProfileErrorCode;
import com.ai.mall.member.application.port.AvatarStorage;
import com.ai.mall.member.application.port.IdentityProfileSeedClient;
import com.ai.mall.member.application.port.IdentityProfileSeedClient.ProfileSeed;
import com.ai.mall.member.domain.model.member.AvatarFormat;
import com.ai.mall.member.support.ApiTestSecurityConfig;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 会员资料接口 HTTP 全链路（CHG-0016 STORY-003-01-02-01）：
 * TC-001 视图与认证隔离 / TC-002 修改校验 / TC-003 懒补偿 / TC-004 头像 / TC-005 拒绝矩阵。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("STORY-003-01-02-01 /api/mall/members/me")
class MemberProfileApiTest {

    private static final long MEMBER_ID = 72001111L;
    private static final String PROVISION_BODY =
            "{\"eventId\":\"evt-me-1\",\"memberId\":\"72001111\",\"username\":\"Me_User\","
                    + "\"nickname\":\"会员001111\",\"occurredAt\":\"2026-09-15T10:00:00Z\"}";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @MockitoBean AvatarStorage avatarStorage;
    @MockitoBean IdentityProfileSeedClient seedClient;

    @Value("${mall.security.internal.shared-secret}")
    String sharedSecret;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM member_profile WHERE member_id IN (72001111, 72002222, 72003333)");
    }

    // ---------- TC-001 ----------

    @Test
    @DisplayName("TC-001 GET /me 返回 memberId(字符串)/username/昵称/头像/手机/邮箱")
    void getMeReturnsStringIdProfile() throws Exception {
        provisionProfile();

        mockMvc.perform(get("/api/mall/members/me").header("Authorization", "Bearer " + memberToken(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value("72001111"))
                .andExpect(jsonPath("$.data.memberId").isString())
                .andExpect(jsonPath("$.data.username").value("Me_User"))
                .andExpect(jsonPath("$.data.nickname").value("会员001111"))
                .andExpect(jsonPath("$.data.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.gender").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.phone").value(nullValue()))
                .andExpect(jsonPath("$.data.email").value(nullValue()));
    }

    @Test
    @DisplayName("无令牌 → 401；ADMIN 令牌访问会员接口 → 403（双向隔离）")
    void anonymous401AndAdmin403() throws Exception {
        mockMvc.perform(get("/api/mall/members/me"))
                .andExpect(status().isUnauthorized());

        provisionProfile();
        mockMvc.perform(get("/api/mall/members/me").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isForbidden());
    }

    // ---------- TC-002 ----------

    @Test
    @DisplayName("TC-002 PUT /me 合法部分更新 200；空昵称/超长昵称/坏手机/坏邮箱/坏性别 → 400")
    void updateMeValidationMatrix() throws Exception {
        provisionProfile();

        mockMvc.perform(put("/api/mall/members/me")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gender\":\"male\",\"phone\":\"\",\"email\":\"new@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("会员001111"))
                .andExpect(jsonPath("$.data.gender").value("MALE"))
                .andExpect(jsonPath("$.data.phone").value(nullValue()))
                .andExpect(jsonPath("$.data.email").value("new@example.com"));

        expect400("{\"nickname\":\"\"}", "昵称");
        expect400("{\"nickname\":\"" + "字".repeat(33) + "\"}", "昵称");
        expect400("{\"phone\":\"12345\"}", null);
        expect400("{\"email\":\"not-an-email\"}", null);
        expect400("{\"gender\":\"OTHER\"}", null);

        // 坏请求不写库：性别仍为 MALE、邮箱仍为 new@example.com
        assertThat(jdbc.queryForObject(
                "SELECT gender FROM member_profile WHERE member_id = 72001111", String.class)).isEqualTo("MALE");
    }

    // ---------- TC-003 ----------

    @Test
    @DisplayName("TC-003 手删 profile 后 GET /me：seed 懒补偿重建（默认昵称/确定性事件），再调不重复取 seed")
    void lazyCompensationRebuildsOnce() throws Exception {
        provisionProfile();
        jdbc.update("DELETE FROM member_profile WHERE member_id = 72001111");
        when(seedClient.fetchSeed(MEMBER_ID))
                .thenReturn(new ProfileSeed(MEMBER_ID, "Me_User", "ENABLED"));

        mockMvc.perform(get("/api/mall/members/me").header("Authorization", "Bearer " + memberToken(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value("72001111"))
                .andExpect(jsonPath("$.data.username").value("Me_User"))
                .andExpect(jsonPath("$.data.nickname").value("会员001111"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_profile WHERE member_id = 72001111", Integer.class)).isEqualTo(1);
        String expectedEventId = java.util.UUID.nameUUIDFromBytes(
                "member-profile-seed:72001111".getBytes(StandardCharsets.UTF_8)).toString();
        assertThat(jdbc.queryForObject(
                "SELECT initialized_event_id FROM member_profile WHERE member_id = 72001111", String.class))
                .isEqualTo(expectedEventId);

        mockMvc.perform(get("/api/mall/members/me").header("Authorization", "Bearer " + memberToken(MEMBER_ID)))
                .andExpect(status().isOk());
        verify(seedClient, times(1)).fetchSeed(MEMBER_ID);
    }

    @Test
    @DisplayName("懒补偿 seed 返回认证不一致（404/401 语义）→ 401")
    void seedInconsistentReturns401() throws Exception {
        when(seedClient.fetchSeed(72003333L)).thenThrow(
                new BusinessException(MemberProfileErrorCode.PROFILE_INCONSISTENT, HttpStatus.UNAUTHORIZED));

        mockMvc.perform(get("/api/mall/members/me")
                        .header("Authorization", "Bearer " + memberToken(72003333L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("B0101"));
    }

    // ---------- TC-004 ----------

    @Test
    @DisplayName("TC-004 PNG 头像上传 200 → avatarUrl 可访问、库内更新、contentType=image/png 传存储")
    void avatarUploadSuccess() throws Exception {
        provisionProfile();
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        String expectedUrl = "http://localhost:9000/mall-avatar/member-avatar/72001111/u.png";
        when(avatarStorage.uploadAvatar(eq(MEMBER_ID), any(), eq(AvatarFormat.PNG))).thenReturn(expectedUrl);

        mockMvc.perform(multipart("/api/mall/members/me/avatar")
                        .file(new MockMultipartFile("file", "x.png", "image/png", png))
                        .header("Authorization", "Bearer " + memberToken(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(expectedUrl));

        assertThat(jdbc.queryForObject(
                "SELECT avatar_url FROM member_profile WHERE member_id = 72001111", String.class))
                .isEqualTo(expectedUrl);
        verify(avatarStorage).uploadAvatar(eq(MEMBER_ID), any(), eq(AvatarFormat.PNG));
    }

    // ---------- TC-005 ----------

    @Test
    @DisplayName("TC-005 伪装 gif（GIF89a 魔数）→ 400 FILE_TYPE_INVALID，对象存储零写入")
    void disguisedGifRejected() throws Exception {
        provisionProfile();
        byte[] gif = "GIF89a fake gif content".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/mall/members/me/avatar")
                        .file(new MockMultipartFile("file", "disguised.png", "image/png", gif))
                        .header("Authorization", "Bearer " + memberToken(MEMBER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0101"));

        verify(avatarStorage, never()).uploadAvatar(anyLong(), any(), any());
        assertThat(jdbc.queryForObject(
                "SELECT avatar_url FROM member_profile WHERE member_id = 72001111", String.class)).isNull();
    }

    @Test
    @DisplayName("TC-005 2.1MB（PNG 魔数）→ 400 FILE_TOO_LARGE，对象存储零写入")
    void oversizedRejected() throws Exception {
        provisionProfile();
        byte[] oversized = new byte[2 * 1024 * 1024 + 1024];
        oversized[0] = (byte) 0x89;
        oversized[1] = 0x50;
        oversized[2] = 0x4E;
        oversized[3] = 0x47;

        mockMvc.perform(multipart("/api/mall/members/me/avatar")
                        .file(new MockMultipartFile("file", "big.png", "image/png", oversized))
                        .header("Authorization", "Bearer " + memberToken(MEMBER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0102"));

        verify(avatarStorage, never()).uploadAvatar(anyLong(), any(), any());
    }

    @Test
    @DisplayName("存储故障 → 503 STORAGE_UNAVAILABLE，库内不写半成品 URL")
    void storageFailure503() throws Exception {
        provisionProfile();
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1};
        when(avatarStorage.uploadAvatar(anyLong(), any(), any())).thenThrow(
                new BusinessException(MemberProfileErrorCode.STORAGE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE));

        mockMvc.perform(multipart("/api/mall/members/me/avatar")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", jpeg))
                        .header("Authorization", "Bearer " + memberToken(MEMBER_ID)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("S0102"));

        assertThat(jdbc.queryForObject(
                "SELECT avatar_url FROM member_profile WHERE member_id = 72001111", String.class)).isNull();
    }

    // ---------- helpers ----------

    private void expect400(String jsonBody, String messageFragment) throws Exception {
        var result = mockMvc.perform(put("/api/mall/members/me")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andReturn();
        if (messageFragment != null) {
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains(messageFragment);
        }
    }

    private void provisionProfile() throws Exception {
        jdbc.update("DELETE FROM member_profile WHERE member_id = 72001111");
        mockMvc.perform(post("/api/internal/members/provision")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROVISION_BODY))
                .andExpect(status().isOk());
    }

    private String memberToken(long memberId) {
        var claims = JwtClaimsSet.builder()
                .subject(Long.toString(memberId)).claim("username", "Me_User").claim("subject_type", "MEMBER")
                .claim("auth_version", 1).audience(List.of(ApiTestSecurityConfig.AUDIENCE))
                .issuer(ApiTestSecurityConfig.ISSUER)
                .issuedAt(Instant.now().minusSeconds(5)).expiresAt(Instant.now().plusSeconds(600))
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
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
