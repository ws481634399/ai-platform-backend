package com.ai.mall.inventory.interfaces.rest.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.inventory.support.ApiTestSecurityConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 库存管理端 API 集成测试：CHG-0015 TC-005 分页 total 回归 + ID 字符串契约。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("库存管理端 API")
class InventoryAdminApiTest {

    /** 回归阈值：超过 32 行，旧无分页插件场景 total 恒为 0/单页数。 */
    private static final int ROW_COUNT = 33;

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc.execute("DELETE FROM inventory_stock");
        for (long skuId = 1; skuId <= ROW_COUNT; skuId++) {
            jdbc.update("INSERT INTO inventory_stock(id, sku_id, total_quantity, locked_quantity, "
                            + "created_at, updated_at, version) VALUES (?, ?, 100, 0, NOW(), NOW(), 0)",
                    skuId, skuId);
        }
    }

    @Test
    @DisplayName("TC-005 33 行库存分页：首页 total=33 且翻页 total 恒定；skuId 字符串、数量 number")
    void paginationTotalStableAcrossPages() throws Exception {
        // 第 1 页：10 条，total=33
        mockMvc.perform(get("/api/admin/inventory/stocks").param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + token(List.of("inventory:stock:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(ROW_COUNT))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.records.length()").value(10))
                // ID 字符串（雪花契约），数量保持数字
                .andExpect(jsonPath("$.data.records[0].skuId").isString())
                .andExpect(jsonPath("$.data.records[0].totalQuantity").isNumber())
                .andExpect(jsonPath("$.data.records[0].availableQuantity").value(100));

        // 第 4 页：3 条，total 仍为 33（验证不是当页条数）
        mockMvc.perform(get("/api/admin/inventory/stocks").param("page", "4").param("size", "10")
                        .header("Authorization", "Bearer " + token(List.of("inventory:stock:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(ROW_COUNT))
                .andExpect(jsonPath("$.data.records.length()").value(3));
    }

    @Test
    @DisplayName("无库存列表权限 → 403")
    void permissionEnforced() throws Exception {
        mockMvc.perform(get("/api/admin/inventory/stocks")
                        .header("Authorization", "Bearer " + token(List.of())))
                .andExpect(status().isForbidden());
    }

    private String token(List<String> permissions) {
        var claims = JwtClaimsSet.builder()
                .subject("1003").claim("username", "inventory_admin")
                .claim("subject_type", "ADMIN").claim("auth_version", 1)
                .audience(List.of(ApiTestSecurityConfig.AUDIENCE))
                .issuer(ApiTestSecurityConfig.ISSUER)
                .issuedAt(java.time.Instant.now().minusSeconds(5))
                .expiresAt(java.time.Instant.now().plusSeconds(600));
        if (permissions != null && !permissions.isEmpty()) {
            claims.claim("permissions", permissions);
        }
        return encoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }
}
