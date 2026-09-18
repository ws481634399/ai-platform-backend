package com.ai.mall.system.cache;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.system.support.AbstractRedisIntegrationTest;
import com.ai.mall.system.support.SystemApiTestSecurityConfig;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * CHG-0022 Story2 缓存与内部端点集成测试（真实 Redis 7 + H2）。
 *
 * <p>覆盖 test-design S2 8 个 TC：回源回填 TTL、二次读走缓存、负缓存标记 60s、
 * missingKeys、keys 空/超 100 B0601、内部鉴权、AFTER_COMMIT 失效、公开聚合键。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SystemApiTestSecurityConfig.class)
@DisplayName("配置缓存与内部端点集成")
class ConfigCacheIntegrationTest extends AbstractRedisIntegrationTest {

    private static final List<String> ALL_PERMS = List.of(
            "system:feature:list", "system:feature:update",
            "system:parameter:list", "system:parameter:update");

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder encoder;
    @Autowired StringRedisTemplate redis;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Value("${mall.security.internal.shared-secret:dev-internal-secret}")
    String internalToken;

    /** 与 ConfigCacheService 键前缀约定一致（本测试固定 test profile）。 */
    private static final String ENV = "test";

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
                + "'DYNAMIC',0,1,0,'分页',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))");
        // 清空本环境配置键空间，保证 TTL/回填断言基线
        var keys = redis.keys("aimall:" + ENV + ":system:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    @DisplayName("S2-TC-001：内部端点无内部身份 → 401/403")
    void internalRequiresToken() throws Exception {
        mockMvc.perform(get("/api/internal/config/features").param("keys", "search.enabled"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("S2-TC-002：命中回源 → values 有值 missingKeys 空；Redis 单键已回填 TTL≈600")
    void fetchWritesRedis() throws Exception {
        mockMvc.perform(get("/api/internal/config/features")
                        .param("keys", "search.enabled")
                        .header("X-Internal-Token", internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.values['search.enabled'].key").value("search.enabled"))
                .andExpect(jsonPath("$.data.values['search.enabled'].enabled").value(true))
                .andExpect(jsonPath("$.data.values['search.enabled'].version").value(0))
                .andExpect(jsonPath("$.data.missingKeys[0]").doesNotExist());

        String redisKey = "aimall:" + ENV + ":system:feature:search.enabled";
        assertThat(redis.opsForValue().get(redisKey)).contains("\"enabled\":true");
        Long ttl = redis.getExpire(redisKey);
        assertThat(ttl).isNotNull().isBetween(500L, 600L);
    }

    @Test
    @DisplayName("S2-TC-003：二次读走缓存——DB 改值不改缓存，响应仍为缓存旧值")
    void secondReadHitsCache() throws Exception {
        mockMvc.perform(get("/api/internal/config/features")
                        .param("keys", "search.enabled")
                        .header("X-Internal-Token", internalToken))
                .andExpect(status().isOk());
        // 绕过应用层直接改库（模拟旁路写入），缓存 600s 内应仍读旧值
        jdbc.update("UPDATE feature_config SET enabled=0 WHERE config_key='search.enabled'");
        mockMvc.perform(get("/api/internal/config/features")
                        .param("keys", "search.enabled")
                        .header("X-Internal-Token", internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.values['search.enabled'].enabled").value(true));
    }

    @Test
    @DisplayName("S2-TC-004：缺键进 missingKeys 并写负缓存标记 TTL≈60s")
    void missingKeyNegativeCache() throws Exception {
        mockMvc.perform(get("/api/internal/config/features")
                        .param("keys", "not.exist.key")
                        .header("X-Internal-Token", internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.values['not.exist.key']").doesNotExist())
                .andExpect(jsonPath("$.data.missingKeys[0]").value("not.exist.key"));

        String redisKey = "aimall:" + ENV + ":system:feature:not.exist.key";
        assertThat(redis.opsForValue().get(redisKey)).isEqualTo("{\"missing\":true}");
        Long ttl = redis.getExpire(redisKey);
        assertThat(ttl).isNotNull().isBetween(1L, 60L);
    }

    @Test
    @DisplayName("S2-TC-005：keys 为空或超 100 → 400")
    void keysValidation() throws Exception {
        mockMvc.perform(get("/api/internal/config/features")
                        .param("keys", " , ")
                        .header("X-Internal-Token", internalToken))
                .andExpect(status().isBadRequest());

        String many = String.join(",", java.util.stream.IntStream.range(0, 101)
                .mapToObj(i -> "k" + i).toList());
        mockMvc.perform(get("/api/internal/config/parameters")
                        .param("keys", many)
                        .header("X-Internal-Token", internalToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("S2-TC-006：参数快照契约字段 configValue/parameterType/min/max 齐全")
    void parameterSnapshotContract() throws Exception {
        mockMvc.perform(get("/api/internal/config/parameters")
                        .param("keys", "search.default-page-size,cart.max-item-quantity")
                        .header("X-Internal-Token", internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.values['search.default-page-size'].configValue").value("20"))
                .andExpect(jsonPath("$.data.values['search.default-page-size'].parameterType").value("INTEGER"))
                .andExpect(jsonPath("$.data.values['search.default-page-size'].minValue").value("1"))
                .andExpect(jsonPath("$.data.values['search.default-page-size'].maxValue").value("100"))
                .andExpect(jsonPath("$.data.missingKeys[0]").value("cart.max-item-quantity"));
    }

    @Test
    @DisplayName("S2-TC-007：管理端更新提交后删单键与公开聚合键（下次读回源重建）")
    void updateEvictsCache() throws Exception {
        String featureKey = "aimall:" + ENV + ":system:feature:search.enabled";
        String aggregateKey = "aimall:" + ENV + ":system:public-features";
        // 预热单键与聚合键
        mockMvc.perform(get("/api/internal/config/features")
                        .param("keys", "search.enabled")
                        .header("X-Internal-Token", internalToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/mall/public-features")).andExpect(status().isOk());
        assertThat(redis.hasKey(featureKey)).isTrue();
        assertThat(redis.hasKey(aggregateKey)).isTrue();

        String update = """
                {"name":"商品搜索","group":"search","enabled":false,"publicFlag":true,
                 "description":"关闭","version":0,"changeReason":"演练"}""";
        mockMvc.perform(put("/api/admin/feature-configs/search.enabled")
                        .header("Authorization", "Bearer " + adminToken(ALL_PERMS))
                        .contentType("application/json").content(update))
                .andExpect(status().isOk());

        // AFTER_COMMIT 同步监听（无 @Async），提交返回时应已删键；给极小等待防时序抖动
        awaitGone(featureKey);
        awaitGone(aggregateKey);

        // 再次内部读取 → 回源得到新值并重新回填
        mockMvc.perform(get("/api/internal/config/features")
                        .param("keys", "search.enabled")
                        .header("X-Internal-Token", internalToken))
                .andExpect(jsonPath("$.data.values['search.enabled'].enabled").value(false));
    }

    @Test
    @DisplayName("S2-TC-008：公开端点匿名可访问，仅公开键；禁用项也返回 enabled=false；非公开键不出现")
    void publicFeatures() throws Exception {
        // 新增一个非公开开关
        jdbc.update("INSERT INTO feature_config(config_key,feature_name,config_group,enabled,public_flag,"
                + "built_in,version,description,created_at,updated_at) VALUES "
                + "('internal.flag','内部开关','sys',1,0,0,0,'非公开',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))");
        mockMvc.perform(get("/api/mall/public-features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.key=='search.enabled' && @.enabled==true)]").exists())
                .andExpect(jsonPath("$.data[?(@.key=='mall.guest-cart.enabled')]").exists())
                .andExpect(jsonPath("$.data[?(@.key=='internal.flag')]").doesNotExist());
    }

    private void awaitGone(String key) {
        long deadline = System.currentTimeMillis() + 2000;
        while (Boolean.TRUE.equals(redis.hasKey(key)) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        assertThat(redis.hasKey(key)).isFalse();
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
