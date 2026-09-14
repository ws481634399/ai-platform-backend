package com.ai.mall.inventory.interfaces.rest.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.inventory.application.inventory.InventoryApplicationService;
import com.ai.mall.inventory.domain.inventory.InventoryReservation;
import com.ai.mall.inventory.support.ApiTestSecurityConfig;
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
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 库存内部接口安全测试（CHG-0015 凭证模式）：
 * 无凭证/错误凭证/合法 ADMIN JWT 一律 401，仅 X-Internal-Token 正确时放行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
class InternalInventorySecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder encoder;
    @MockitoBean InventoryApplicationService service;

    @Value("${mall.security.internal.shared-secret}")
    String sharedSecret;

    private static final String LOCK_BODY =
            "{\"reservationId\":\"R-1\",\"skuId\":1,\"quantity\":1}";

    @Test
    @DisplayName("TC-003a 无凭证访问内部接口 → 401 INTERNAL_UNAUTHORIZED")
    void noCredentialUnauthorized() throws Exception {
        mockMvc.perform(post("/api/internal/inventory/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("TC-003b 错误 X-Internal-Token → 401")
    void wrongTokenUnauthorized() throws Exception {
        mockMvc.perform(post("/api/internal/inventory/lock")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("TC-003c 携带合法 ADMIN JWT 但无内部凭证 → 401（JWT 不通内部）")
    void adminJwtWithoutTokenUnauthorized() throws Exception {
        mockMvc.perform(post("/api/internal/inventory/lock")
                        .header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("TC-003d 正确 X-Internal-Token → 200")
    void correctTokenAccepted() throws Exception {
        when(service.lock(any())).thenReturn(new InventoryReservation("R-1", 1L, 1L));

        mockMvc.perform(post("/api/internal/inventory/lock")
                        .header(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isOk());
    }

    private String token(String subjectType) {
        var claims = JwtClaimsSet.builder()
                .subject("inventory-caller")
                .claim("username", "inventory-caller")
                .claim("subject_type", subjectType)
                .claim("auth_version", 1)
                .audience(List.of(ApiTestSecurityConfig.AUDIENCE))
                .issuer(ApiTestSecurityConfig.ISSUER)
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
