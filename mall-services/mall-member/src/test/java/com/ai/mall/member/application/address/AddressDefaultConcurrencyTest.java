package com.ai.mall.member.application.address;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * TC-006 并发设默认（CHG-0016 STORY-003-01-03-01）：
 * 两线程同一起跑线把「无默认会员」的不同地址设默认，default_member_flag uk 兜底，
 * 最终该会员全表仅一条 is_default=1；冲突方异常码为 B0203（409 转译见
 * AddressApplicationServiceTest 确定性单测；MySQL InnoDB 在语句级阻塞后报错，
 * 真实两进程复跑归 M3 Test）。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("STORY-003-01-03-01 并发 setDefault uk 兜底")
class AddressDefaultConcurrencyTest {

    private static final long MEMBER_D = 73004444L;

    @Autowired AddressApplicationService addresses;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("两线程并发 setDefault 不同地址：最终仅一个默认，不出现双默认")
    void concurrentSetDefaultStaysUnique() throws Exception {
        Instant now = Instant.now();
        jdbc.update("DELETE FROM shipping_address WHERE member_id = 73004444");
        for (int i = 1; i <= 3; i++) {
            jdbc.update("INSERT INTO shipping_address(member_id, receiver_name, receiver_phone, province, city, "
                            + "district, detail_address, postal_code, is_default, created_at, updated_at) "
                            + "VALUES(73004444, ?, '13800000004', '浙江省', '杭州市', '西湖区', ?, NULL, 0, ?, ?)",
                    "收件" + i, "地址" + i, now, now);
        }
        Long id1 = jdbc.queryForObject(
                "SELECT id FROM shipping_address WHERE member_id = 73004444 ORDER BY id LIMIT 1", Long.class);
        Long id2 = jdbc.queryForObject(
                "SELECT id FROM shipping_address WHERE member_id = 73004444 ORDER BY id DESC LIMIT 1", Long.class);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = pool.submit(() -> race(id1, ready, start));
            Future<?> f2 = pool.submit(() -> race(id2, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertOneOutcome(f1);
            assertOneOutcome(f2);
        } finally {
            pool.shutdownNow();
        }

        Integer defaultCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM shipping_address WHERE member_id = 73004444 AND is_default = 1",
                Integer.class);
        assertThat(defaultCount).isEqualTo(1);
    }

    private void race(Long addressId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        addresses.setDefault(MEMBER_D, addressId);
    }

    /** 任一线程失败必须是 409 默认冲突；若引擎把竞争串行了，则两成功也合法（最终仍唯一）。 */
    private void assertOneOutcome(Future<?> future) throws Exception {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause();
            assertThat(cause).isInstanceOf(com.ai.mall.common.web.exception.BusinessException.class);
            assertThat(((com.ai.mall.common.web.exception.BusinessException) cause).getErrorCode().getCode())
                    .isEqualTo("B0203");
        }
    }
}
