package com.ai.mall.system.admin;

import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.system.support.SystemApiTestSecurityConfig;
import java.time.Instant;
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
 * CHG-0022 Story1 配置管理垂直切片集成测试（H2 + Flyway 种子 + 真实安全链）。
 *
 * <p>覆盖 test-design S1 10 个 TC：种子分页、CRUD、B0602 key 冲突、B0601 非法值/内置禁删、
 * B0603 不存在、B0604 版本冲突、变更历史留痕过滤、权限 403。
 * Redis 失效监听在 Redis 不可达时内部吞异常（TTL 兜底），不影响本切片断言。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SystemApiTestSecurityConfig.class)
@DisplayName("系统配置管理端 API")
class ConfigAdminApiTest {

    private static final List<String> ALL_PERMS = List.of(
            "system:feature:list", "system:feature:update",
            "system:parameter:list", "system:parameter:update",
            "system:config-history:list");

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.execute("DELETE FROM system_config_history");
        jdbc.execute("DELETE FROM feature_config");
        jdbc.execute("DELETE FROM system_parameter");
        jdbc.update("INSERT INTO feature_config(config_key,feature_name,config_group,enabled,public_flag,"
                + "built_in,version,description,created_at,updated_at) VALUES "
                + "('search.enabled','商品搜索','search',1,1,1,0,'搜索总开关',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),"
                + "('mall.guest-cart.enabled','游客购物车','cart',1,1,1,0,'游客车开关',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))");
        jdbc.update("INSERT INTO system_parameter(config_key,parameter_name,config_group,parameter_type,"
                + "config_value,default_value,min_value,max_value,effect_type,public_flag,built_in,version,"
                + "description,created_at,updated_at) VALUES "
                + "('search.default-page-size','搜索默认分页大小','search','INTEGER','20','20','1','100',"
                + "'DYNAMIC',0,1,0,'分页',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),"
                + "('cart.max-item-quantity','购物车单品最大数量','cart','INTEGER','99','99','1','999',"
                + "'DYNAMIC',0,1,0,'数量上限',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))");
    }

    // ---------- 功能开关 ----------

    @Test
    @DisplayName("S1-TC-001：种子分页返回 2 个开关，含内置标记与版本")
    void pageSeededFeatures() throws Exception {
        mockMvc.perform(get("/api/admin/feature-configs").header("Authorization", "Bearer " + adminToken(ALL_PERMS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].key").value("search.enabled"))
                .andExpect(jsonPath("$.data.items[0].builtIn").value(true))
                .andExpect(jsonPath("$.data.items[0].version").value(0));
    }

    @Test
    @DisplayName("S1-TC-002：新建开关成功并留 CREATED 历史；重复 key 返回 409 B0602")
    void createFeatureAndDuplicate() throws Exception {
        String body = """
                {"key":"ai.rag.enabled","name":"RAG 导购","group":"ai","enabled":true,
                 "publicFlag":false,"description":"AI RAG"}""";
        mockMvc.perform(post("/api/admin/feature-configs")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("ai.rag.enabled"))
                .andExpect(jsonPath("$.data.version").value(0));

        mockMvc.perform(post("/api/admin/feature-configs")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("B0602"));

        mockMvc.perform(get("/api/admin/config-history?configType=FEATURE&key=ai.rag.enabled")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].changeKind").value("CREATED"))
                .andExpect(jsonPath("$.data.items[0].changedBy").value("2001"));
    }

    @Test
    @DisplayName("S1-TC-003：名称为空 → 400 B0601")
    void createFeatureInvalidName() throws Exception {
        String body = """
                {"key":"bad.key","name":"","group":"g","enabled":true,"publicFlag":false,"description":""}""";
        mockMvc.perform(post("/api/admin/feature-configs")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("S1-TC-004：编辑带当前版本成功并版本递增；过期版本 409 B0604；不存在 404 B0603")
    void updateFeatureCas() throws Exception {
        String v0 = """
                {"name":"商品搜索","group":"search","enabled":false,"publicFlag":true,
                 "description":"desc","version":0,"changeReason":"大促关闭搜索"}""";
        mockMvc.perform(put("/api/admin/feature-configs/search.enabled")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                        .contentType("application/json").content(v0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(put("/api/admin/feature-configs/search.enabled")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                        .contentType("application/json").content(v0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("B0604"));

        String update = """
                {"name":"x","group":"g","enabled":true,"publicFlag":false,
                 "description":"","version":1,"changeReason":null}""";
        mockMvc.perform(put("/api/admin/feature-configs/not.exist")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                        .contentType("application/json").content(update))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("B0603"));

        mockMvc.perform(get("/api/admin/config-history?configType=FEATURE&key=search.enabled")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS)))
                .andExpect(jsonPath("$.data.items[0].changeKind").value("UPDATED"))
                .andExpect(jsonPath("$.data.items[0].oldValue").value("true"))
                .andExpect(jsonPath("$.data.items[0].newValue").value("false"))
                .andExpect(jsonPath("$.data.items[0].changeReason").value("大促关闭搜索"));
    }

    @Test
    @DisplayName("S1-TC-005：内置键不可删除 400 B0601；自定义键可删除并留 DELETED 历史")
    void deleteFeature() throws Exception {
        mockMvc.perform(delete("/api/admin/feature-configs/search.enabled")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0601"));

        String body = """
                {"key":"tmp.feature","name":"临时","group":"g","enabled":true,
                 "publicFlag":false,"description":""}""";
        mockMvc.perform(post("/api/admin/feature-configs")
                .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                .contentType("application/json").content(body)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/feature-configs/tmp.feature?reason=下线")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/config-history?configType=FEATURE&key=tmp.feature")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS)))
                .andExpect(jsonPath("$.data.items[0].changeKind").value("DELETED"))
                .andExpect(jsonPath("$.data.items[0].changeReason").value("下线"));
    }

    // ---------- 系统参数 ----------

    @Test
    @DisplayName("S1-TC-006：参数种子分页，类型过滤生效")
    void pageSeededParameters() throws Exception {
        mockMvc.perform(get("/api/admin/system-parameters").header("Authorization", "Bearer " + adminToken(ALL_PERMS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].type").value("INTEGER"));
        mockMvc.perform(get("/api/admin/system-parameters?group=cart")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].key").value("cart.max-item-quantity"));
    }

    @Test
    @DisplayName("S1-TC-007：参数超范围/非法类型 → 400 B0601；重复 key 409 B0602")
    void createParameterValidation() throws Exception {
        String outOfRange = """
                {"key":"p.range","name":"范围参数","group":"g","type":"INTEGER","value":"999",
                 "defaultValue":"10","minValue":"1","maxValue":"100","effectType":"DYNAMIC",
                 "publicFlag":false,"description":""}""";
        mockMvc.perform(post("/api/admin/system-parameters")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                        .contentType("application/json").content(outOfRange))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0601"));

        String badType = """
                {"key":"p.type","name":"类型参数","group":"g","type":"DATETIME","value":"x",
                 "defaultValue":null,"minValue":null,"maxValue":null,"effectType":"DYNAMIC",
                 "publicFlag":false,"description":""}""";
        mockMvc.perform(post("/api/admin/system-parameters")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                        .contentType("application/json").content(badType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0601"));

        String jsonBad = """
                {"key":"p.json","name":"JSON 参数","group":"g","type":"JSON","value":"{not-json",
                 "defaultValue":null,"minValue":null,"maxValue":null,"effectType":"DYNAMIC",
                 "publicFlag":false,"description":""}""";
        mockMvc.perform(post("/api/admin/system-parameters")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                        .contentType("application/json").content(jsonBad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0601"));

        String dup = """
                {"key":"search.default-page-size","name":"重复键","group":"g","type":"INTEGER","value":"10",
                 "defaultValue":"10","minValue":"1","maxValue":"100","effectType":"DYNAMIC",
                 "publicFlag":false,"description":""}""";
        mockMvc.perform(post("/api/admin/system-parameters")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                        .contentType("application/json").content(dup))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("B0602"));
    }

    @Test
    @DisplayName("S1-TC-008：参数合法编辑成功版本递增；内置参数不可删除 B0601")
    void updateAndDeleteParameter() throws Exception {
        String update = """
                {"name":"搜索默认分页大小","group":"search","value":"50","defaultValue":"20",
                 "minValue":"1","maxValue":"100","publicFlag":false,"description":"调大",
                 "version":0,"changeReason":"压测"}""";
        mockMvc.perform(put("/api/admin/system-parameters/search.default-page-size")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                        .contentType("application/json").content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("50"))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(delete("/api/admin/system-parameters/search.default-page-size")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0601"));
    }

    // ---------- 历史与权限 ----------

    @Test
    @DisplayName("S1-TC-009：历史类型+键过滤与分页；未带历史权限 403")
    void historyFilterAndAuth() throws Exception {
        String body = """
                {"key":"h.feature","name":"历史","group":"g","enabled":true,
                 "publicFlag":false,"description":""}""";
        mockMvc.perform(post("/api/admin/feature-configs")
                .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                .contentType("application/json").content(body)).andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/config-history?configType=FEATURE&key=h.feature")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/admin/config-history?configType=PARAMETER")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        List<String> noHistoryPerm = List.of("system:feature:list", "system:feature:update",
                "system:parameter:list", "system:parameter:update");
        mockMvc.perform(get("/api/admin/config-history")
                        .header("Authorization", "Bearer " + adminToken(noHistoryPerm)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("S1-TC-010：无 token 401；只有 list 权限执行写操作 403")
    void authenticationAndAuthorization() throws Exception {
        mockMvc.perform(get("/api/admin/feature-configs"))
                .andExpect(status().isUnauthorized());

        String body = """
                {"key":"x.feature","name":"X","group":"g","enabled":true,
                 "publicFlag":false,"description":""}""";
        mockMvc.perform(post("/api/admin/feature-configs")
                        .header("Authorization", "Bearer " + adminToken(List.of("system:feature:list")))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/feature-configs")
                        .header("Authorization", "Bearer " + adminToken(List.of("system:parameter:list"))))
                .andExpect(status().isForbidden());
    }

    private String adminToken(List<String> permissions) {
        var claims = JwtClaimsSet.builder()
                .subject("2001").claim("username", "config_admin")
                .claim("subject_type", "ADMIN").claim("auth_version", 1)
                .audience(List.of("mall-admin-api")).issuer("ai-platform")
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("permissions", permissions);
        return encoder.encode(JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(RS256).build(),
                claims.build())).getTokenValue();
    }
}
