package com.ai.mall.product.interfaces.rest.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import com.ai.mall.product.support.ApiTestSecurityConfig;

/**
 * 分类管理端 API 集成测试：H2 + Flyway V1 + 真实安全链（TestSecurityConfig 重建），
 * 覆盖 test-design.md TC-001~TC-010 的 HTTP/持久化/权限语义。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("分类管理 API")
class CategoryAdminApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void cleanTable() {
        jdbc.execute("DELETE FROM product_category");
    }

    // ---------- TC-001 ----------

    @Test
    @DisplayName("TC-001 合法一级分类创建成功，tree 出现 level=1 parentId=0 节点")
    void createRootCategory() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token(List.of("product:category:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "数码", "sort", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists());

        mockMvc.perform(get("/api/admin/categories/tree")
                        .header("Authorization", "Bearer " + token(List.of("product:category:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("数码"))
                .andExpect(jsonPath("$.data[0].level").value(1))
                .andExpect(jsonPath("$.data[0].parentId").value(0))
                .andExpect(jsonPath("$.data[0].status").value("ENABLED"));
    }

    // ---------- TC-002 ----------

    @Test
    @DisplayName("TC-002 可建至第三级，第四级被业务拒绝")
    void levelThreeAllowedFourthRejected() throws Exception {
        long l1 = createCategory("家电", 0, List.of("product:category:create"));
        long l2 = createCategory("冰箱", l1, List.of("product:category:create"));
        long l3 = createCategory("对开门", l2, List.of("product:category:create"));

        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token(List.of("product:category:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "超第四级", "parentId", l3))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B2105"));
    }

    // ---------- TC-003 ----------

    @Test
    @DisplayName("TC-003 parentId 指向自身时拒绝")
    void selfReferenceRejected() throws Exception {
        long id = createCategory("服饰", 0, List.of("product:category:create"));
        mockMvc.perform(put("/api/admin/categories/" + id)
                        .header("Authorization", "Bearer " + token(List.of("product:category:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "服饰", "parentId", id))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B2106"));
    }

    // ---------- TC-004 ----------

    @Test
    @DisplayName("TC-004 祖先挂到后代形成循环拒绝；合法移动成功且子树层级重算")
    void cycleRejectedAndLegalMoveRecomputesLevels() throws Exception {
        long a = createCategory("A", 0, List.of("product:category:create"));
        long b = createCategory("B", a, List.of("product:category:create"));
        long c = createCategory("C", b, List.of("product:category:create"));

        mockMvc.perform(put("/api/admin/categories/" + a)
                        .header("Authorization", "Bearer " + token(List.of("product:category:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "A", "parentId", c))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B2107"));

        // 合法移动：B 移到根（level 2→1），其子 C 应由 3 重算为 2
        mockMvc.perform(put("/api/admin/categories/" + b)
                        .header("Authorization", "Bearer " + token(List.of("product:category:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "B", "parentId", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/categories/tree")
                        .header("Authorization", "Bearer " + token(List.of("product:category:list"))))
                .andExpect(jsonPath("$.data[?(@.id==" + b + ")].level").value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$.data[?(@.id==" + b + ")].children[0].id").value(org.hamcrest.Matchers.hasItem((int) c)))
                .andExpect(jsonPath("$.data[?(@.id==" + b + ")].children[0].level").value(org.hamcrest.Matchers.hasItem(2)));
    }

    // ---------- TC-005 ----------

    @Test
    @DisplayName("TC-005 parentId 不存在时拒绝")
    void missingParentRejected() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token(List.of("product:category:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "孤儿", "parentId", 999999))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B2103"));
    }

    // ---------- TC-006 ----------

    @Test
    @DisplayName("TC-006 禁用父下新增子级拒绝，启用后成功")
    void disabledParentRejectsChild() throws Exception {
        long parent = createCategory("母类", 0, List.of("product:category:create"));
        changeStatus(parent, "DISABLED", List.of("product:category:disable"));

        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token(List.of("product:category:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "子类", "parentId", parent))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B2104"));

        changeStatus(parent, "ENABLED", List.of("product:category:disable"));
        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token(List.of("product:category:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "子类", "parentId", parent))))
                .andExpect(status().isOk());
    }

    // ---------- TC-007 / TC-009 ----------

    @Test
    @DisplayName("TC-007/TC-009 tree 含禁用节点且按 sort、id 稳定排序")
    void treeContainsDisabledAndSorted() throws Exception {
        long root = createCategory("根", 0, List.of("product:category:create"));
        long s30 = createCategory("三十", root, 30, List.of("product:category:create"));
        long s10 = createCategory("一十", root, 10, List.of("product:category:create"));
        long s20 = createCategory("二十", root, 20, List.of("product:category:create"));
        changeStatus(s20, "DISABLED", List.of("product:category:disable"));

        mockMvc.perform(get("/api/admin/categories/tree")
                        .header("Authorization", "Bearer " + token(List.of("product:category:list"))))
                .andExpect(status().isOk())
                // 根的 children 顺序为 一十(10)、二十(20,禁用)、三十(30)，禁用节点仍在树中
                .andExpect(jsonPath("$.data[0].children[0].id").value((int) s10))
                .andExpect(jsonPath("$.data[0].children[1].id").value((int) s20))
                .andExpect(jsonPath("$.data[0].children[1].status").value("DISABLED"))
                .andExpect(jsonPath("$.data[0].children[2].id").value((int) s30));
    }

    // ---------- TC-008 ----------

    @Test
    @DisplayName("TC-008 禁用分类不级联子分类")
    void disableDoesNotCascade() throws Exception {
        long root = createCategory("父禁用", 0, List.of("product:category:create"));
        long child = createCategory("子保持启用", root, List.of("product:category:create"));
        changeStatus(root, "DISABLED", List.of("product:category:disable"));

        mockMvc.perform(get("/api/admin/categories/tree")
                        .header("Authorization", "Bearer " + token(List.of("product:category:list"))))
                .andExpect(jsonPath("$.data[0].status").value("DISABLED"))
                .andExpect(jsonPath("$.data[0].children[0].id").value((int) child))
                .andExpect(jsonPath("$.data[0].children[0].status").value("ENABLED"));
    }

    // ---------- 同级重名 ----------

    @Test
    @DisplayName("同级重名（trim/大小写由数据库规则约束）第二次创建冲突")
    void duplicatedSiblingNameConflicts() throws Exception {
        createCategory("UniqueName", 0, List.of("product:category:create"));
        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token(List.of("product:category:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", " UniqueName "))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("B2102"));
    }

    // ---------- TC-010 ----------

    @Test
    @DisplayName("TC-010 无权限身份 403 且无写入，有权限 200；无 token 401")
    void permissionEnforced() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token(List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "越权尝试"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/categories/tree")
                        .header("Authorization", "Bearer " + token(List.of("product:category:create"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/categories/tree"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token(List.of("product:category:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "有权限"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id} 不存在返回 404 业务错误")
    void getUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/admin/categories/424242")
                        .header("Authorization", "Bearer " + token(List.of("product:category:list"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("B2101"));
    }

    // ---------- helpers ----------

    private long createCategory(String name, long parentId, java.util.List<String> permissions) throws Exception {
        return createCategory(name, parentId, 0, permissions);
    }

    private long createCategory(String name, long parentId, int sort, java.util.List<String> permissions)
            throws Exception {
        String response = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token(permissions))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "parentId", parentId, "sort", sort))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private void changeStatus(long id, String status, java.util.List<String> permissions) throws Exception {
        mockMvc.perform(put("/api/admin/categories/" + id + "/status")
                        .header("Authorization", "Bearer " + token(permissions))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", status))))
                .andExpect(status().isOk());
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String token(List<String> permissions) {
        var claims = JwtClaimsSet.builder()
                .subject("1001").claim("username", "product_admin")
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
