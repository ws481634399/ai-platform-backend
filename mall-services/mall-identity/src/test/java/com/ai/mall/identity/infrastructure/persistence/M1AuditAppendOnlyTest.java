package com.ai.mall.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.mall.common.core.trace.TraceContext;
import com.ai.mall.identity.infrastructure.persistence.audit.MyBatisAuditRecorder;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * CHG-0008/0009 补充证据：BE-209 审计日志 traceId 落库、敏感值不落审计、append-only 只增不改。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("M1 审计日志只增不改测试")
class M1AuditAppendOnlyTest {

    @Autowired MyBatisAuditRecorder recorder;
    @Autowired MyBatisAuditRecorder.AuditMapper auditMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("BE-209/TC-001 审计记录携带 traceId 持久化并可回溯")
    void auditRowPersistsTraceId() {
        String traceId = "abcdef0123456789abcdef0123456789";
        TraceContext.set(traceId);
        try {
            recorder.record(9, "ROLE_CREATE", "ROLE", "ops_admin", "SUCCESS", null);
        } finally {
            TraceContext.clear();
        }

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT actor_admin_id, action, target_type, target_id, result, trace_id \n"
                        + "FROM auth_audit_log WHERE trace_id = ?", traceId);
        assertThat(row.get("actor_admin_id").toString()).isEqualTo("9");
        assertThat(row.get("action")).isEqualTo("ROLE_CREATE");
        assertThat(row.get("target_type")).isEqualTo("ROLE");
        assertThat(row.get("target_id")).isEqualTo("ops_admin");
        assertThat(row.get("result")).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("BE-209/TC-002 审计详情不记录敏感值（仅 reason），失败也留下可审计结果")
    void auditRowCarriesFailureReasonWithoutSensitiveValues() {
        recorder.record(3, "ADMIN_DISABLE", "ADMIN", "7", "FAILURE", "LAST_SUPER_ADMIN");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT trace_id, CAST(detail_json AS VARCHAR) AS detail_json, result FROM auth_audit_log \n"
                        + "WHERE action = 'ADMIN_DISABLE' AND target_id = '7' AND result = 'FAILURE' \n"
                        + "ORDER BY id DESC LIMIT 1");
        assertThat(row.get("result").toString()).isEqualTo("FAILURE");
        assertThat(row.get("trace_id")).isNull();
        String detail = row.get("detail_json") == null ? "" : row.get("detail_json").toString();
        assertThat(detail).doesNotContain("password").doesNotContain("hash").contains("LAST_SUPER_ADMIN");
    }

    @Test
    @DisplayName("BE-209/TC-003 审计写入只增不改：无 UPDATE/DELETE，仅单个 @Insert 方法")
    void auditTableIsAppendOnly() throws Exception {
        var proxy = auditMapper.getClass();
        assertThat(proxy.getInterfaces()).contains(MyBatisAuditRecorder.AuditMapper.class);
        var methods = MyBatisAuditRecorder.AuditMapper.class.getMethod("insert",
                long.class, String.class, String.class, String.class, String.class, String.class, String.class);
        List<Annotation> annotations = List.of(methods.getAnnotations());
        List<Class<? extends Annotation>> names = annotations.stream().map(Annotation::annotationType).toList();
        assertThat(names).contains(Insert.class).doesNotContain(Update.class).doesNotContain(Delete.class);

        long before = jdbc.queryForObject("SELECT COUNT(*) FROM auth_audit_log", Long.class);
        recorder.record(1, "MENU_CREATE", "MENU", "/dashboard/x", "SUCCESS", null);
        long after = jdbc.queryForObject("SELECT COUNT(*) FROM auth_audit_log", Long.class);
        assertThat(after).isEqualTo(before + 1);

        String ddl = new String(new ClassPathResource("db/migration/V1__create_admin_auth_and_rbac.sql").getInputStream().readAllBytes());
        assertThat(ddl).contains("CREATE TABLE auth_audit_log");
        assertThat(ddl).doesNotContain("UPDATE auth_audit_log").doesNotContain("DELETE FROM auth_audit_log")
                .doesNotContain("TRUNCATE auth_audit_log").contains("created_at");
    }
}