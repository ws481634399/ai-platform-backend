package com.ai.mall.order.infrastructure.persistence.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * DelayTaskAdminMapper union 三源 H2(Flyway) 集成测试（TC-007）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(statements = {"DELETE FROM outbox_event", "DELETE FROM orders"})
class DelayTaskAdminMapperIntegrationTest {

    @Autowired
    private DelayTaskAdminMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;

    /** 插入订单最小必填列。 */
    private void insertOrder(long id, String orderNo, String status, String cancelReason, Instant at) {
        jdbc.update("INSERT INTO orders(id, order_no, member_id, status, source, goods_amount, pay_amount, "
                        + "receiver_name, receiver_phone, receiver_province, receiver_city, receiver_district, "
                        + "receiver_detail_address, created_at, updated_at, cancel_reason, cancelled_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, orderNo, 5001L, status, "CART", 2000L, 2000L,
                "张三", "13800000000", "浙江省", "杭州市", "西湖区", "文一路 1 号",
                Timestamp.from(at), Timestamp.from(at), cancelReason,
                cancelReason != null ? Timestamp.from(at) : null);
    }

    private void insertFailedOutbox(long id, String aggregateId, String lastError, Instant at) {
        jdbc.update("INSERT INTO outbox_event(id, aggregate_id, event_type, delay_level, payload, status, "
                        + "last_error, created_at) VALUES (?,?,?,?,?,?,?,?)",
                id, aggregateId, "PAYMENT_TIMEOUT_CHECK", 16, "{\"eventId\":\"x\"}", "FAILED",
                lastError, Timestamp.from(at));
    }

    @Test
    void unionAll_collectsThreeSourcesWithFilters() {
        Instant now = Instant.now();
        insertOrder(1L, "ON1", "PENDING_PAYMENT", null, now.minusSeconds(300));
        insertOrder(2L, "ON2", "CANCELLED", "PAYMENT_TIMEOUT", now.minusSeconds(400));
        // 用户主动取消与已支付单均不属于延迟任务
        insertOrder(3L, "ON3", "CANCELLED", "USER_CANCELLED", now.minusSeconds(500));
        insertOrder(4L, "ON4", "PAID", null, now.minusSeconds(600));
        // FAILED 源：关联到存在的订单 4
        insertFailedOutbox(100L, "4", "broker exhausted", now.minusSeconds(700));

        List<DelayTaskRow> all = mapper.selectTasks(null, 0, 100);

        assertThat(all).hasSize(3);
        assertThat(all).extracting(DelayTaskRow::getDelayStatus)
                .containsExactlyInAnyOrder("PENDING", "CANCELLED", "FAILED");
        assertThat(mapper.countTasks(null)).isEqualTo(3L);

        // 排序：created_at 最新在前 → PENDING(1) 首条
        assertThat(all.get(0).getDelayStatus()).isEqualTo("PENDING");

        // FAILED 行取关联订单的 orderNo，并携带 lastError
        DelayTaskRow failed = all.stream().filter(r -> r.getDelayStatus().equals("FAILED")).findFirst().orElseThrow();
        assertThat(failed.getOrderNo()).isEqualTo("ON4");
        assertThat(failed.getLastError()).isEqualTo("broker exhausted");

        // 状态筛选
        assertThat(mapper.selectTasks("PENDING", 0, 100)).hasSize(1);
        assertThat(mapper.countTasks("CANCELLED")).isEqualTo(1L);
    }

    @Test
    void failedSource_orderVanished_fallsBackToAggregateId() {
        insertFailedOutbox(101L, "999", "lost", Instant.now().minusSeconds(10));

        List<DelayTaskRow> rows = mapper.selectTasks("FAILED", 0, 100);

        assertThat(rows).hasSize(1);
        // 订单缺失 → orderNo 回退显示 aggregate_id
        assertThat(rows.get(0).getOrderId()).isNull();
        assertThat(rows.get(0).getOrderNo()).isEqualTo("999");
    }

    @Test
    void paging_limitOffsetApplies() {
        Instant now = Instant.now();
        insertOrder(11L, "ON11", "PENDING_PAYMENT", null, now.minusSeconds(10));
        insertOrder(12L, "ON12", "PENDING_PAYMENT", null, now.minusSeconds(20));

        List<DelayTaskRow> page1 = mapper.selectTasks("PENDING", 0, 1);
        List<DelayTaskRow> page2 = mapper.selectTasks("PENDING", 1, 1);

        assertThat(page1).hasSize(1);
        assertThat(page2).hasSize(1);
        assertThat(page1.get(0).getOrderNo()).isEqualTo("ON11");
        assertThat(page2.get(0).getOrderNo()).isEqualTo("ON12");
    }
}
