package com.ai.mall.inventory.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("库存预留状态机")
class InventoryReservationTest {

    @Test
    @DisplayName("新建预留为 LOCKED 状态")
    void newReservationIsLocked() {
        var r = new InventoryReservation("r1", 1L, 10L);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.LOCKED);
    }

    @Test
    @DisplayName("释放幂等：已释放再释放不变")
    void releaseIdempotent() {
        var r = new InventoryReservation("r1", 1L, 10L);
        r.release();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        r.release();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    @DisplayName("确认扣减幂等：已扣减再扣减不变")
    void confirmIdempotent() {
        var r = new InventoryReservation("r1", 1L, 10L);
        r.confirmDeduction();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.DEDUCTED);
        r.confirmDeduction();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.DEDUCTED);
    }

    @Test
    @DisplayName("重建：状态按持久化值恢复")
    void reconstitutePreservesStatus() {
        Instant now = Instant.now();
        var r = InventoryReservation.reconstitute(1L, "r1", 1L, 10L,
                ReservationStatus.RELEASED, now, now);
        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }
}
