package com.ai.mall.inventory.application.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai.mall.inventory.application.inventory.InventoryCommands.ConfirmCommand;
import com.ai.mall.inventory.application.inventory.InventoryCommands.LockCommand;
import com.ai.mall.inventory.application.inventory.InventoryCommands.ReleaseCommand;
import com.ai.mall.inventory.domain.inventory.InventoryException;
import com.ai.mall.inventory.domain.inventory.InventoryReservation;
import com.ai.mall.inventory.domain.inventory.ReservationStatus;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * CHG-0019 REQ-M4-002 库存 release/confirm CAS 加固测试（H2 + 真实仓储/服务）。
 *
 * <p>验证：终态重复调用幂等不重复改数量；同一预留并发 release 只产生一次数量变化、
 * locked 不会被扣成负数；release/confirm 互斥（已扣减不能释放）。
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryReleaseConfirmCasTest {

    @Autowired private InventoryApplicationService inventoryService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("DELETE FROM inventory_reservation");
        jdbc.execute("DELETE FROM inventory_log");
        jdbc.execute("DELETE FROM inventory_stock");
    }

    private void seedStock(long skuId, long total, long locked) {
        jdbc.update("INSERT INTO inventory_stock(id, sku_id, total_quantity, locked_quantity, "
                + "created_at, updated_at, version) VALUES (?, ?, ?, ?, NOW(), NOW(), 0)",
                skuId, skuId, total, locked);
    }

    private long[] lockedCounts(long skuId) {
        MapRow row = query(skuId);
        return new long[] {row.total, row.locked};
    }

    private MapRow query(long skuId) {
        return jdbc.queryForObject("SELECT total_quantity, locked_quantity FROM inventory_stock WHERE sku_id = ?",
                (rs, rowNum) -> new MapRow(rs.getLong(1), rs.getLong(2)), skuId);
    }

    private record MapRow(long total, long locked) {
    }

    @Test
    @DisplayName("release CAS：重复释放幂等，locked 只扣一次且不为负")
    void releaseIdempotent() {
        seedStock(3001L, 100L, 0L);
        InventoryReservation locked = inventoryService.lock(new LockCommand("ORD-R1:3001", 3001L, 30L));
        assertEquals(ReservationStatus.LOCKED, locked.getStatus());

        InventoryReservation first = inventoryService.release(new ReleaseCommand("ORD-R1:3001"));
        InventoryReservation second = inventoryService.release(new ReleaseCommand("ORD-R1:3001"));
        assertEquals(ReservationStatus.RELEASED, first.getStatus());
        assertEquals(ReservationStatus.RELEASED, second.getStatus());
        long[] counts = lockedCounts(3001L);
        assertEquals(100L, counts[0]);
        assertEquals(0L, counts[1], "重复释放不得二次扣减 locked");
        Long releaseLogs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_log WHERE operation_type = 'RELEASE' AND business_id = ?",
                Long.class, "ORD-R1:3001");
        assertEquals(1L, releaseLogs, "幂等重复不应写第二条流水");
    }

    @Test
    @DisplayName("confirm CAS：重复确认幂等，total/locked 只扣一次")
    void confirmIdempotent() {
        seedStock(3002L, 100L, 0L);
        inventoryService.lock(new LockCommand("ORD-C1:3002", 3002L, 30L));

        inventoryService.confirmDeduction(new ConfirmCommand("ORD-C1:3002"));
        inventoryService.confirmDeduction(new ConfirmCommand("ORD-C1:3002"));
        long[] counts = lockedCounts(3002L);
        assertEquals(70L, counts[0]);
        assertEquals(0L, counts[1]);
    }

    @Test
    @DisplayName("release/confirm 互斥：已确认扣减的预留不能再释放（B2207 409，数量保持一致）")
    void confirmAndReleaseExclusive() {
        seedStock(3003L, 100L, 0L);
        inventoryService.lock(new LockCommand("ORD-X1:3003", 3003L, 30L));
        inventoryService.confirmDeduction(new ConfirmCommand("ORD-X1:3003"));
        assertThrows(InventoryException.class,
                () -> inventoryService.release(new ReleaseCommand("ORD-X1:3003")));
        long[] counts = lockedCounts(3003L);
        assertEquals(70L, counts[0]);
        assertEquals(0L, counts[1]);
    }

    @Test
    @DisplayName("并发重复 release：CAS 保证恰一次数量变化，locked 终态为 0 不为负")
    void concurrentReleaseCas() throws Exception {
        seedStock(3004L, 100L, 0L);
        inventoryService.lock(new LockCommand("ORD-P1:3004", 3004L, 30L));

        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger idempotent = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        InventoryReservation result = inventoryService.release(new ReleaseCommand("ORD-P1:3004"));
                        if (result.getStatus() == ReservationStatus.RELEASED) {
                            // 首次迁移或终态幂等返回都可能是 RELEASED，用 locked 终态兜底断言
                            success.incrementAndGet();
                        }
                    } catch (Exception ex) {
                        errors.incrementAndGet();
                    }
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
        }
        assertEquals(0, errors.get(), "并发释放不应产生异常（落败方走幂等/冲突语义）");
        long[] counts = lockedCounts(3004L);
        assertEquals(100L, counts[0]);
        assertEquals(0L, counts[1], "并发下 locked 必须恰好扣减一次");
        assertEquals(0, idempotent.get());
    }
}
