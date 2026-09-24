package com.ai.mall.order.infrastructure.persistence.order;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 延迟取消任务管理查询 Mapper（STORY-009-04-01）。
 *
 * <p>UNION ALL 三同构源（派生列 delay_status）：
 * <ol>
 *   <li>PENDING —— orders 中 PENDING_PAYMENT；</li>
 *   <li>CANCELLED —— orders 中 CANCELLED 且 cancel_reason='PAYMENT_TIMEOUT'；</li>
 *   <li>FAILED —— outbox_event 中 PAYMENT_TIMEOUT_CHECK/FAILED，LEFT JOIN orders
 *       取 order_no；订单缺失回退 aggregate_id。</li>
 * </ol>
 * 标准 SQL（CAST/COALESCE/LIMIT），H2 MySQL 模式与 MySQL 均兼容，不使用 JSON 函数。
 */
@Mapper
public interface DelayTaskAdminMapper {

    /** 三源 UNION 主体（分页与计数查询共用）。 */
    String UNION_SQL = """
            SELECT order_id, order_no, delay_status, created_at, cancelled_at, last_error FROM (
              SELECT id AS order_id, order_no, 'PENDING' AS delay_status, created_at,
                     NULL AS cancelled_at, NULL AS last_error
              FROM orders WHERE status = 'PENDING_PAYMENT'
              UNION ALL
              SELECT id, order_no, 'CANCELLED', created_at, cancelled_at, NULL
              FROM orders WHERE status = 'CANCELLED' AND cancel_reason = 'PAYMENT_TIMEOUT'
              UNION ALL
              SELECT o.id, COALESCE(o.order_no, e.aggregate_id), 'FAILED', e.created_at,
                     NULL, e.last_error
              FROM outbox_event e
              LEFT JOIN orders o ON o.id = CAST(e.aggregate_id AS BIGINT)
              WHERE e.event_type = 'PAYMENT_TIMEOUT_CHECK' AND e.status = 'FAILED'
            ) delay_tasks\
            """;

    /** 分页查询：status 为空返回全部；按创建时间倒序。 */
    @Select("<script>" + UNION_SQL
            + " <where><if test='status != null and status != \"\"'> AND delay_status = #{status}</if></where>"
            + " ORDER BY created_at DESC, order_id ASC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<DelayTaskRow> selectTasks(@Param("status") String status,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    /** 计数查询（分页 total）。 */
    @Select("<script>SELECT COUNT(*) FROM (" + UNION_SQL
            + ") counted <where><if test='status != null and status != \"\"'>"
            + " AND delay_status = #{status}</if></where></script>")
    long countTasks(@Param("status") String status);
}
