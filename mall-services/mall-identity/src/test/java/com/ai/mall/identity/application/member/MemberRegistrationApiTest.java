package com.ai.mall.identity.application.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 会员注册接口集成测试（CHG-0016 / TC-001~TC-003、TC-008）。
 *
 * <p>MemberProvisioner 以 MockitoBean 替换真实 HTTP 客户端：注册事务 afterCommit
 * 同步触发一次投递（成功 → outbox DONE），不发起真实网络调用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("STORY-003-01-01-01 会员注册 API")
class MemberRegistrationApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean MemberProvisioner provisioner;

    @Test
    @DisplayName("TC-001/TC-008 合规注册 → 201 memberId 字符串；BCrypt 落库；afterCommit 投递 DONE；全程无明文密码")
    void validRegistrationReturnsStringIdAndProvisions() throws Exception {
        String rawPassword = "Abcd1234";
        String responseBody = mockMvc.perform(post("/api/auth/member/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Alice_1\",\"password\":\"" + rawPassword + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").exists())
                .andReturn().getResponse().getContentAsString();

        // memberId 必须是 JSON 字符串（雪花/自增 ID 不出 number）
        JsonNode data = objectMapper.readTree(responseBody).get("data");
        assertThat(data.get("memberId").isTextual()).isTrue();
        String memberIdText = data.get("memberId").asText();
        // 响应不含任何密码字段/明文
        assertThat(responseBody).doesNotContain(rawPassword).doesNotContain("password");

        // member_user：原始大小写 + 归一化小写 + BCrypt 哈希（无明文）
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_user WHERE username = 'Alice_1' AND username_norm = 'alice_1'",
                Integer.class)).isEqualTo(1);
        String passwordHash = jdbc.queryForObject(
                "SELECT password_hash FROM member_user WHERE username_norm = 'alice_1'", String.class);
        assertThat(passwordHash).startsWith("$2a$12$").doesNotContain(rawPassword);

        // outbox：同事务写入，afterCommit 同步投递成功后 DONE；payload 不含密码
        long memberId = Long.parseLong(memberIdText);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM member_event_outbox WHERE member_id = ?",
                String.class, memberId)).isEqualTo("DONE");
        String payload = jdbc.queryForObject(
                "SELECT payload_json FROM member_event_outbox WHERE member_id = ?",
                String.class, memberId);
        assertThat(payload).contains("\"memberId\":\"" + memberIdText + "\"")
                .contains("Alice_1").doesNotContain(rawPassword).doesNotContain("password");

        verify(provisioner, timeout(2_000)).provision(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("TC-002 大小写变体重复注册 → 第二个 409「用户名已存在」，member_user 仅一行")
    void duplicateUsernameCaseInsensitiveConflict() throws Exception {
        mockMvc.perform(post("/api/auth/member/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Dup_User\",\"password\":\"Abcd1234\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/member/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dUp_user\",\"password\":\"Abcd1234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户名已存在"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_user WHERE username_norm = 'dup_user'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("TC-003 用户名/密码规则矩阵违例 → 400 字段级提示")
    void invalidUsernameOrPasswordRejectedWithFieldMessages() throws Exception {
        // 用户名 <4 / 数字开头 / 含非法字符
        expect400("{\"username\":\"abc\",\"password\":\"Abcd1234\"}", "用户名");
        expect400("{\"username\":\"1abcd\",\"password\":\"Abcd1234\"}", "用户名");
        expect400("{\"username\":\"ab-cd\",\"password\":\"Abcd1234\"}", "用户名");
        // 密码 <8 / 纯数字 / 纯字母
        expect400("{\"username\":\"abcd\",\"password\":\"Ab123\"}", "密码");
        expect400("{\"username\":\"abcd\",\"password\":\"12345678\"}", "密码");
        expect400("{\"username\":\"abcd\",\"password\":\"abcdefgh\"}", "密码");
    }

    @Test
    @DisplayName("空白字段 → 400（Bean Validation 字段级提示）")
    void blankFieldsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/member/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"Abcd1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("用户名不能为空")));
    }

    @Test
    @DisplayName("400 场景不写库、不触发投递")
    void rejectionHasNoSideEffects() throws Exception {
        expect400("{\"username\":\"9abc_invalid\",\"password\":\"Abcd1234\"}", "用户名");
        // 共享上下文内只断言被拒用户名无任何残留
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_user WHERE username_norm = '9abc_invalid'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_event_outbox WHERE payload_json LIKE '%9abc_invalid%'",
                Integer.class)).isZero();
        verifyNoInteractions(provisioner);
    }

    private void expect400(String body, String messagePart) throws Exception {
        mockMvc.perform(post("/api/auth/member/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(messagePart)));
    }
}
