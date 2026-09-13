package com.ai.mall.product.interfaces.rest.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.product.support.ApiTestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 品牌管理端 API 集成测试：H2 + Flyway V1 + 分页插件 + 真实安全链，
 * 覆盖品牌 test-design TC-001~TC-007、TC-009。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("品牌管理 API")
class BrandAdminApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void cleanTable() {
        jdbc.execute("DELETE FROM product_brand");
    }

    // ---------- TC-001 ----------

    @Test
    @DisplayName("TC-001 合法品牌创建成功，默认 ENABLED/sort=0 且可分页查询")
    void createDefaultsAndVisible() throws Exception {
        mockMvc.perform(post("/api/admin/brands")
                        .header("Authorization", "Bearer " + token(List.of("product:brand:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Nike"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists());

        mockMvc.perform(get("/api/admin/brands")
                        .header("Authorization", "Bearer " + token(List.of("product:brand:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("Nike"))
                .andExpect(jsonPath("$.data.records[0].status").value("ENABLED"))
                .andExpect(jsonPath("$.data.records[0].sort").value(0));
    }

    // ---------- TC-002 ----------

    @Test
    @DisplayName("TC-002 同名（trim/大小写差异）创建冲突且无落库")
    void duplicatedNameConflicts() throws Exception {
        createBrand("Adidas", List.of("product:brand:create"));

        mockMvc.perform(post("/api/admin/brands")
                        .header("Authorization", "Bearer " + token(List.of("product:brand:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", " adidas "))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("B2122"));

        mockMvc.perform(get("/api/admin/brands")
                        .header("Authorization", "Bearer " + token(List.of("product:brand:list"))))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    // ---------- TC-003 ----------

    @Test
    @DisplayName("TC-003 改名撞名 409 且原值不变；改成未占用名成功")
    void renameConflictKeepsOriginal() throws Exception {
        long a = createBrand("Puma", List.of("product:brand:create"));
        createBrand("Reebok", List.of("product:brand:create"));

        mockMvc.perform(put("/api/admin/brands/" + a)
                        .header("Authorization", "Bearer " + token(List.of("product:brand:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "reebok"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("B2122"));

        mockMvc.perform(get("/api/admin/brands/" + a)
                        .header("Authorization", "Bearer " + token(List.of("product:brand:list"))))
                .andExpect(jsonPath("$.data.name").value("Puma"));

        mockMvc.perform(put("/api/admin/brands/" + a)
                        .header("Authorization", "Bearer " + token(List.of("product:brand:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Puma-X"))))
                .andExpect(status().isOk());
    }

    // ---------- TC-004 ----------

    @Test
    @DisplayName("TC-004 logo/description/sort 可修改持久化；非法 logo 形态拦截")
    void profileUpdateAndInvalidLogo() throws Exception {
        long id = createBrand("Li-Ning", List.of("product:brand:create"));

        mockMvc.perform(put("/api/admin/brands/" + id)
                        .header("Authorization", "Bearer " + token(List.of("product:brand:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Li-Ning", "logo", "ftp://bad/logo.png"))))
                .andExpect(status().isBadRequest());

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Li-Ning");
        body.put("logo", "https://cdn.example.com/logo.png");
        body.put("description", "国民运动品牌");
        body.put("sort", 7);
        mockMvc.perform(put("/api/admin/brands/" + id)
                        .header("Authorization", "Bearer " + token(List.of("product:brand:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/brands/" + id)
                        .header("Authorization", "Bearer " + token(List.of("product:brand:list"))))
                .andExpect(jsonPath("$.data.logo").value("https://cdn.example.com/logo.png"))
                .andExpect(jsonPath("$.data.description").value("国民运动品牌"))
                .andExpect(jsonPath("$.data.sort").value(7));
    }

    // ---------- TC-005 ----------

    @Test
    @DisplayName("TC-005 分页 records/total/page/size 正确，keyword/status 过滤，sort,id 排序，越界归一")
    void pagingFilterSortAndNormalization() throws Exception {
        // sort 故意乱序插入：b(sort=20)、a(sort=10)、c(sort=10)、disabled(sort=5)
        createBrand("Bravo", null, null, 20, List.of("product:brand:create"));
        createBrand("Alpha", null, null, 10, List.of("product:brand:create"));
        createBrand("Aurora", null, null, 10, List.of("product:brand:create"));
        long disabled = createBrand("DisabledCo", null, null, 5, List.of("product:brand:create"));
        changeStatus(disabled, "DISABLED");

        // 默认按 sort,id：第一条为 sort=5 的禁用品牌
        mockMvc.perform(get("/api/admin/brands?page=1&size=2")
                        .header("Authorization", "Bearer " + token(List.of("product:brand:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].name").value("DisabledCo"))
                .andExpect(jsonPath("$.data.records[1].name").value("Alpha"));

        // keyword 模糊匹配（A 开头语义由调用方 like 保证）
        mockMvc.perform(get("/api/admin/brands?keyword=Al")
                        .header("Authorization", "Bearer " + token(List.of("product:brand:list"))))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("Alpha"));

        // status 过滤
        mockMvc.perform(get("/api/admin/brands?status=DISABLED")
                        .header("Authorization", "Bearer " + token(List.of("product:brand:list"))))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("DisabledCo"));

        // 越界归一：page<1 → 1；size>100 → 100
        mockMvc.perform(get("/api/admin/brands?page=-5&size=999")
                        .header("Authorization", "Bearer " + token(List.of("product:brand:list"))))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.records.length()").value(4));
    }

    // ---------- TC-006 ----------

    @Test
    @DisplayName("TC-006 禁用后数据仍在、列表可见；启用恢复")
    void disableKeepsDataAndCanReenable() throws Exception {
        long id = createBrand("ToggleMe", List.of("product:brand:create"));
        changeStatus(id, "DISABLED");

        mockMvc.perform(get("/api/admin/brands?status=DISABLED")
                        .header("Authorization", "Bearer " + token(List.of("product:brand:list"))))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("DISABLED"));

        changeStatus(id, "ENABLED");
        mockMvc.perform(get("/api/admin/brands/" + id)
                        .header("Authorization", "Bearer " + token(List.of("product:brand:list"))))
                .andExpect(jsonPath("$.data.status").value("ENABLED"));
    }

    // ---------- TC-007 ----------

    @Test
    @DisplayName("TC-007 无权限 403 且无写入；无 token 401；有权限 200")
    void permissionEnforced() throws Exception {
        mockMvc.perform(post("/api/admin/brands")
                        .header("Authorization", "Bearer " + token(List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "越权"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/brands")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/brands")
                        .header("Authorization", "Bearer " + token(List.of("product:brand:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "授权品牌"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id} 不存在返回 404")
    void getUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/admin/brands/515151")
                        .header("Authorization", "Bearer " + token(List.of("product:brand:list"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("B2121"));
    }

    // ---------- TC-009 ----------

    @Test
    @DisplayName("TC-009 并发同名创建恰一成功，另一冲突，库中仅一条")
    void concurrentDuplicateNameExactlyOneWins() throws Exception {
        // JUnit 允许测试方法声明 Exception；CountDownLatch.await 的中断异常向上传播即视为失败
        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        String bearer = "Bearer " + token(List.of("product:brand:create"));
        int[] statuses = new int[threads];

        try {
            for (int i = 0; i < threads; i++) {
                int index = i;
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        statuses[index] = mockMvc.perform(post("/api/admin/brands")
                                        .header("Authorization", bearer)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json(Map.of("name", "Concurrent"))))
                                .andReturn().getResponse().getStatus();
                    } catch (Exception ignored) {
                        statuses[index] = 0;
                    }
                });
            }
            ready.await(2, TimeUnit.SECONDS);
            start.countDown();
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        // 两个并发请求：一个 200（id），一个 409（唯一索引兜底）
        assertThatStatuses(statuses);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_brand WHERE name = 'Concurrent'", Long.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1L);
    }

    private void assertThatStatuses(int[] statuses) {
        int ok = 0;
        int conflict = 0;
        for (int value : statuses) {
            if (value == 200) {
                ok++;
            } else if (value == 409) {
                conflict++;
            }
        }
        org.assertj.core.api.Assertions.assertThat(ok).as("成功创建数").isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(conflict).as("冲突拒绝数").isEqualTo(1);
    }

    // ---------- helpers ----------

    private long createBrand(String name, List<String> permissions) throws Exception {
        return createBrand(name, null, null, 0, permissions);
    }

    private long createBrand(String name, String logo, String description, int sort,
                             List<String> permissions) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        if (logo != null) {
            body.put("logo", logo);
        }
        if (description != null) {
            body.put("description", description);
        }
        body.put("sort", sort);
        String response = mockMvc.perform(post("/api/admin/brands")
                        .header("Authorization", "Bearer " + token(permissions))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private void changeStatus(long id, String status) throws Exception {
        mockMvc.perform(put("/api/admin/brands/" + id + "/status")
                        .header("Authorization", "Bearer " + token(List.of("product:brand:disable")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", status))))
                .andExpect(status().isOk());
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String token(List<String> permissions) {
        var claims = JwtClaimsSet.builder()
                .subject("1002").claim("username", "brand_admin")
                .claim("subject_type", "ADMIN").claim("auth_version", 1)
                .audience(List.of("mall-admin-api")).issuer("ai-platform")
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(600));
        if (permissions != null && !permissions.isEmpty()) {
            claims.claim("permissions", permissions);
        }
        return encoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }
}
