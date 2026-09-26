package com.ai.mall.common.mq.consumer;

import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * consumed_event 表 JDBC 幂等实现（INSERT IGNORE 占位 / UPDATE 回写 / DELETE 回滚）。
 * <p>表归属消费方数据库（ mall-inventory 先行建表，requirement-design §5）；
 * 本组件只写 SQL 不建表；表名可配置，默认 consumed_event。</p>
 */
public class ConsumedEventRepository implements IdempotentConsumer {

    /**
     * 合法 SQL 标识符白名单：表名只能来自配置（JDBC 表名无法参数化），
     * 构造时即拒绝非标识符字符，防止表名拼接引入注入。
     */
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;

    public ConsumedEventRepository(JdbcTemplate jdbcTemplate, String tableName) {
        if (tableName == null || !IDENTIFIER.matcher(tableName).matches()) {
            throw new IllegalArgumentException(
                    "非法幂等表名（仅允许字母/下划线开头的 1~64 位标识符）: " + tableName);
        }
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
    }

    @Override
    public TryResult tryConsume(String eventId, String consumerGroup, String eventType,
                                String aggregateId, String traceId) {
        // INSERT IGNORE：唯一键 (event_id, consumer_group) 冲突时静默返回 0 行（MySQL 语义）
        int inserted = jdbcTemplate.update(
                "INSERT IGNORE INTO " + tableName
                        + " (event_id, consumer_group, event_type, aggregate_id, result, trace_id, processed_at)"
                        + " VALUES (?, ?, ?, ?, 'PROCESSING', ?, NOW())",
                eventId, consumerGroup, eventType, aggregateId, traceId);
        return inserted > 0 ? TryResult.FIRST_PROCESSED : TryResult.DUPLICATE;
    }

    @Override
    public void markResult(String eventId, String consumerGroup, Result result) {
        jdbcTemplate.update(
                "UPDATE " + tableName + " SET result = ? WHERE event_id = ? AND consumer_group = ?",
                result.name(), eventId, consumerGroup);
    }

    @Override
    public void markSuccessIfProcessing(String eventId, String consumerGroup) {
        // CAS：仅 PROCESSING 行可被处理链收尾为 SUCCESS；SKIPPED（乱序裁决）保持不覆盖
        jdbcTemplate.update(
                "UPDATE " + tableName
                        + " SET result = 'SUCCESS' WHERE event_id = ? AND consumer_group = ?"
                        + " AND result = 'PROCESSING'",
                eventId, consumerGroup);
    }

    @Override
    public void deletePlaceholder(String eventId, String consumerGroup) {
        jdbcTemplate.update(
                "DELETE FROM " + tableName + " WHERE event_id = ? AND consumer_group = ?",
                eventId, consumerGroup);
    }

    /** 供集成测试建表使用（DU-BE-005 业务迁移字段与本 DDL 对齐） */
    public static String ddl(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (\n"
                + "  id BIGINT PRIMARY KEY AUTO_INCREMENT,\n"
                + "  event_id VARCHAR(64) NOT NULL,\n"
                + "  consumer_group VARCHAR(64) NOT NULL,\n"
                + "  event_type VARCHAR(64) NOT NULL,\n"
                + "  aggregate_id VARCHAR(64) NULL,\n"
                + "  result VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',\n"
                + "  trace_id VARCHAR(64) NULL,\n"
                + "  processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
                + "  UNIQUE KEY uk_event_group (event_id, consumer_group),\n"
                + "  KEY idx_aggregate (aggregate_id)\n"
                + ")";
    }
}
