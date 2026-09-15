package com.ai.mall.member.interfaces.rest.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.member.support.ApiTestSecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 收货地址接口 HTTP 全链路（CHG-0016 STORY-003-01-03-01）：
 * TC-001 新增/首条默认、TC-002 列表排序与归属、TC-003/004 越权矩阵、
 * TC-005 设默认唯一、TC-007 删默认/默认空查、TC-008 400 矩阵、TC-009 上限 409、
 * TC-010 迁移生成列与 uk；TC-006 并发见 AddressDefaultConcurrencyTest。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("STORY-003-01-03-01 /api/mall/shipping-addresses")
class ShippingAddressApiTest {

    private static final long MEMBER_A = 73001111L;
    private static final long MEMBER_B = 73002222L;
    private static final long MEMBER_C = 73003333L;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM shipping_address WHERE member_id IN (73001111, 73002222, 73003333, 73004444)");
    }

    // ---------- TC-001 ----------

    @Test
    @DisplayName("TC-001 首条地址 POST 201 且 isDefault=true；第二条 isDefault=false；id 为字符串")
    void firstAddressAutoDefault() throws Exception {
        long id1 = createAddress(MEMBER_A, validBody("张三", "13800138000", "文三路1号"), true);
        createAddress(MEMBER_A, validBody("李四", "13900139000", "文三路2号"), false);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shipping_address WHERE member_id = 73001111 AND is_default = 1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT default_member_flag FROM shipping_address WHERE id = ?", Long.class, id1))
                .isEqualTo(MEMBER_A);
    }

    // ---------- TC-002 ----------

    @Test
    @DisplayName("TC-002 列表：默认优先、再 updated_at DESC；仅含本人地址；defaultId 回传")
    void listOrderedAndScoped() throws Exception {
        long a1 = createAddress(MEMBER_A, validBody("甲", "13800000001", "A1"), true);
        long a2 = createAddress(MEMBER_A, validBody("乙", "13800000002", "A2"), false);
        long a3 = createAddress(MEMBER_A, validBody("丙", "13800000003", "A3"), false);
        createAddress(MEMBER_B, validBody("B会员", "13800000009", "B1"), true);

        // 更新 a2 使其 updated_at 新于 a3 → 非默认段 a2 在前
        mockMvc.perform(put("/api/mall/shipping-addresses/" + a2)
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("乙改", "13800000002", "A2改")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiverName").value("乙改"));

        mockMvc.perform(get("/api/mall/shipping-addresses").header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultId").value(String.valueOf(a1)))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(a1)))
                .andExpect(jsonPath("$.data.items[0].isDefault").value(true))
                .andExpect(jsonPath("$.data.items[1].id").value(String.valueOf(a2)))
                .andExpect(jsonPath("$.data.items[2].id").value(String.valueOf(a3)));
    }

    // ---------- TC-003 ----------

    @Test
    @DisplayName("TC-003 A 修改 B 的地址 → 404 B0201，B 数据不变")
    void updateOthersAddress404() throws Exception {
        long bId = createAddress(MEMBER_B, validBody("B本人", "13800000009", "B路1号"), true);

        mockMvc.perform(put("/api/mall/shipping-addresses/" + bId)
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("A篡改", "13700000001", "被改")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("B0201"));

        assertThat(jdbc.queryForObject(
                "SELECT receiver_name FROM shipping_address WHERE id = ?", String.class, bId))
                .isEqualTo("B本人");
    }

    @Test
    @DisplayName("修改不存在的地址 id → 404（与越权同文案同码）")
    void updateMissingAddress404() throws Exception {
        mockMvc.perform(put("/api/mall/shipping-addresses/99999999")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("张三", "13800138000", "某处")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("B0201"));
    }

    // ---------- TC-004 ----------

    @Test
    @DisplayName("TC-004 A 删除 B 的地址 → 404；删除自己地址 → 204")
    void deleteMatrix() throws Exception {
        long bId = createAddress(MEMBER_B, validBody("B本人", "13800000009", "B路1号"), true);
        long aId = createAddress(MEMBER_A, validBody("甲", "13800000001", "A1"), true);

        mockMvc.perform(delete("/api/mall/shipping-addresses/" + bId)
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("B0201"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_address WHERE id = ?",
                Integer.class, bId)).isEqualTo(1);

        mockMvc.perform(delete("/api/mall/shipping-addresses/" + aId)
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_address WHERE id = ?",
                Integer.class, aId)).isEqualTo(0);
    }

    // ---------- TC-005 ----------

    @Test
    @DisplayName("TC-005 A setDefault B 地址 → 404；setDefault 自己的新地址：旧默认复位，全表仅一条默认")
    void setDefaultClearsOld() throws Exception {
        long a1 = createAddress(MEMBER_A, validBody("甲", "13800000001", "A1"), true);
        long a2 = createAddress(MEMBER_A, validBody("乙", "13800000002", "A2"), false);
        long b1 = createAddress(MEMBER_B, validBody("B本人", "13800000009", "B1"), true);

        mockMvc.perform(put("/api/mall/shipping-addresses/" + b1 + "/default")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/mall/shipping-addresses/" + a2 + "/default")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(String.valueOf(a2)))
                .andExpect(jsonPath("$.data.isDefault").value(true));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shipping_address WHERE member_id = 73001111 AND is_default = 1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT is_default FROM shipping_address WHERE id = ?", Boolean.class, a1)).isFalse();
        // B 的默认不受影响
        assertThat(jdbc.queryForObject(
                "SELECT is_default FROM shipping_address WHERE id = ?", Boolean.class, b1)).isTrue();
    }

    // ---------- TC-007 ----------

    @Test
    @DisplayName("TC-007 删除默认后不自动重选；GET /default 返回 200 {item:null}；有默认时返回该地址")
    void deleteDefaultThenEmpty() throws Exception {
        long a1 = createAddress(MEMBER_A, validBody("甲", "13800000001", "A1"), true);
        long a2 = createAddress(MEMBER_A, validBody("乙", "13800000002", "A2"), false);

        mockMvc.perform(get("/api/mall/shipping-addresses/default")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.id").value(String.valueOf(a1)));

        mockMvc.perform(delete("/api/mall/shipping-addresses/" + a1)
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/mall/shipping-addresses/default")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item").doesNotExist());
        // a2 仍在且非默认
        assertThat(jdbc.queryForObject("SELECT is_default FROM shipping_address WHERE id = ?",
                Boolean.class, a2)).isFalse();
    }

    // ---------- TC-008 ----------

    @Test
    @DisplayName("TC-008 坏手机/缺姓名/detail 超长/邮编非6位 → 400，且不写库")
    void validationMatrix400() throws Exception {
        expect400(validBody("张三", "12345", "某处"));
        expect400(body("张三", "13800138000", "浙江省", "杭州市", "西湖区", "某处", "310000")
                .replace("\"receiverName\":\"张三\"", "\"receiverName\":\"\""));
        expect400(validBody("张三", "13800138000", "详" .repeat(129)));
        expect400(validBody("张三", "13800138000", "某处").replace("\"postalCode\":null", "\"postalCode\":\"12345\""));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_address WHERE member_id = 73001111",
                Integer.class)).isZero();
    }

    // ---------- TC-009 ----------

    @Test
    @DisplayName("TC-009 已有 20 条后第 21 条 POST → 409 B0202")
    void limit20Conflict() throws Exception {
        Instant now = Instant.now();
        for (int i = 1; i <= 20; i++) {
            jdbc.update("INSERT INTO shipping_address(member_id, receiver_name, receiver_phone, province, city, "
                            + "district, detail_address, postal_code, is_default, created_at, updated_at) "
                            + "VALUES(73003333, ?, '138000000%02d', '浙江省', '杭州市', '西湖区', ?, NULL, ?, ?, ?)"
                            .formatted(i),
                    "收件人" + i, "地址" + i, i == 1, now, now);
        }

        mockMvc.perform(post("/api/mall/shipping-addresses")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_C))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("第21个", "13800000021", "第21条地址")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("B0202"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipping_address WHERE member_id = 73003333",
                Integer.class)).isEqualTo(20);
    }

    // ---------- TC-010 ----------

    @Test
    @DisplayName("TC-010 V2：default_member_flag 生成列与 uk_address_default 存在；同会员两条默认被库拒绝")
    void migrationGeneratedColumnAndUnique() {
        Instant now = Instant.now();
        // 生成列存在且可查询（值由 is_default/member_id 推导）
        List<String> columns = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE UPPER(TABLE_NAME) = 'SHIPPING_ADDRESS' "
                        + "AND UPPER(COLUMN_NAME) = 'DEFAULT_MEMBER_FLAG'", String.class);
        assertThat(columns).hasSize(1);

        // H2 2.x INDEXES 视图以 INDEX_TYPE_NAME('UNIQUE INDEX') 表达唯一（无 IS_UNIQUE 列）；
        // H2 会把约束支持索引自动命名为 <约束名>_INDEX_n，故按约束名前缀匹配。
        List<String> uniqueIndexes = jdbc.queryForList(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES "
                        + "WHERE UPPER(TABLE_NAME) = 'SHIPPING_ADDRESS' "
                        + "AND UPPER(INDEX_TYPE_NAME) LIKE '%UNIQUE%' "
                        + "AND UPPER(INDEX_NAME) LIKE 'UK_ADDRESS_DEFAULT%'", String.class);
        assertThat(uniqueIndexes).hasSize(1);

        jdbc.update("INSERT INTO shipping_address(member_id, receiver_name, receiver_phone, province, city, "
                + "district, detail_address, postal_code, is_default, created_at, updated_at) "
                + "VALUES(73001111, '甲', '13800000001', '浙江省', '杭州市', '西湖区', 'A1', NULL, 1, ?, ?)", now, now);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO shipping_address(member_id, receiver_name, receiver_phone, "
                + "province, city, district, detail_address, postal_code, is_default, created_at, updated_at) "
                + "VALUES(73001111, '乙', '13800000002', '浙江省', '杭州市', '西湖区', 'A2', NULL, 1, ?, ?)", now, now))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_address_default");
        // 非默认行不受 uk 影响（多 NULL）
        jdbc.update("INSERT INTO shipping_address(member_id, receiver_name, receiver_phone, province, city, "
                + "district, detail_address, postal_code, is_default, created_at, updated_at) "
                + "VALUES(73001111, '丙', '13800000003', '浙江省', '杭州市', '西湖区', 'A3', NULL, 0, ?, ?)", now, now);
    }

    // ---------- 认证隔离 ----------

    @Test
    @DisplayName("无令牌 → 401；ADMIN 令牌 → 403（路径层 MEMBER 收口）")
    void anonymous401AndAdmin403() throws Exception {
        mockMvc.perform(get("/api/mall/shipping-addresses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/mall/shipping-addresses").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private long createAddress(long memberId, String body, boolean expectedDefault) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mall/shipping-addresses")
                        .header("Authorization", "Bearer " + memberToken(memberId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.isDefault").value(expectedDefault))
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return root.path("data").path("id").asLong();
    }

    private void expect400(String body) throws Exception {
        mockMvc.perform(post("/api/mall/shipping-addresses")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    private String validBody(String name, String phone, String detail) {
        return body(name, phone, "浙江省", "杭州市", "西湖区", detail, null);
    }

    private String body(String name, String phone, String province, String city, String district,
                        String detail, String postalCode) {
        String postalJson = postalCode == null ? "null" : "\"" + postalCode + "\"";
        return "{\"receiverName\":\"" + name + "\",\"receiverPhone\":\"" + phone + "\","
                + "\"province\":\"" + province + "\",\"city\":\"" + city + "\",\"district\":\"" + district + "\","
                + "\"detailAddress\":\"" + detail + "\",\"postalCode\":" + postalJson + "}";
    }

    private String memberToken(long memberId) {
        var claims = JwtClaimsSet.builder()
                .subject(Long.toString(memberId)).claim("username", "Addr_User").claim("subject_type", "MEMBER")
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
