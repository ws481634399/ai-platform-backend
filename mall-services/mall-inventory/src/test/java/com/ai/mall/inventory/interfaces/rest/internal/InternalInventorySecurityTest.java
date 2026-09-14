package com.ai.mall.inventory.interfaces.rest.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.inventory.application.inventory.InventoryApplicationService;
import com.ai.mall.inventory.domain.inventory.InventoryReservation;
import com.ai.mall.inventory.support.ApiTestSecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
class InternalInventorySecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder encoder;
    @MockitoBean InventoryApplicationService service;

    @Test
    void internalInventoryRequiresServiceIdentity() throws Exception {
        when(service.lock(any())).thenReturn(new InventoryReservation("R-1", 1L, 1L));

        mockMvc.perform(post("/api/internal/inventory/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\":\"R-1\",\"skuId\":1,\"quantity\":1}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/internal/inventory/lock")
                        .header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\":\"R-1\",\"skuId\":1,\"quantity\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/internal/inventory/lock")
                        .header("Authorization", "Bearer " + token("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\":\"R-1\",\"skuId\":1,\"quantity\":1}"))
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
