package com.ai.mall.inventory.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("库存聚合规则")
class InventoryTest {

    @Test
    @DisplayName("初始化：total=入参，locked=0，available=total")
    void initializeSetsQuantities() {
        Inventory inv = Inventory.initialize(1L, 100L);
        assertThat(inv.getSkuId()).isEqualTo(1L);
        assertThat(inv.getTotalQuantity()).isEqualTo(100L);
        assertThat(inv.getLockedQuantity()).isZero();
        assertThat(inv.getAvailableQuantity()).isEqualTo(100L);
    }

    @Test
    @DisplayName("初始化：负库存拒绝")
    void initializeRejectsNegative() {
        assertThatThrownBy(() -> Inventory.initialize(1L, -1L))
                .isInstanceOf(InventoryException.class);
    }

    @Test
    @DisplayName("调整：正数加、负数减，结果不能为负")
    void adjustUpdatesTotal() {
        Inventory inv = Inventory.initialize(1L, 100L);
        inv.adjust(20L);
        assertThat(inv.getTotalQuantity()).isEqualTo(120L);
        inv.adjust(-30L);
        assertThat(inv.getTotalQuantity()).isEqualTo(90L);
        assertThat(inv.getAvailableQuantity()).isEqualTo(90L);
    }

    @Test
    @DisplayName("调整：导致总库存为负时拒绝")
    void adjustRejectsNegativeResult() {
        Inventory inv = Inventory.initialize(1L, 10L);
        assertThatThrownBy(() -> inv.adjust(-11L))
                .isInstanceOf(InventoryException.class);
    }

    @Test
    @DisplayName("锁定/释放：可用 = total - locked，超额锁定拒绝")
    void lockAndRelease() {
        Inventory inv = Inventory.initialize(1L, 100L);
        inv.lock(30L);
        assertThat(inv.getLockedQuantity()).isEqualTo(30L);
        assertThat(inv.getAvailableQuantity()).isEqualTo(70L);

        inv.lock(20L);
        assertThat(inv.getLockedQuantity()).isEqualTo(50L);
        assertThat(inv.getAvailableQuantity()).isEqualTo(50L);

        assertThatThrownBy(() -> inv.lock(51L))
                .isInstanceOf(InventoryException.class);

        inv.release(30L);
        assertThat(inv.getLockedQuantity()).isEqualTo(20L);
        assertThat(inv.getAvailableQuantity()).isEqualTo(80L);
    }

    @Test
    @DisplayName("释放超过已锁定数量时拒绝")
    void releaseRejectsOverRelease() {
        Inventory inv = Inventory.initialize(1L, 100L);
        inv.lock(30L);
        assertThatThrownBy(() -> inv.release(31L))
                .isInstanceOf(InventoryException.class);
    }

    @Test
    @DisplayName("确认扣减：total 和 locked 同时扣减")
    void confirmDeduction() {
        Inventory inv = Inventory.initialize(1L, 100L);
        inv.lock(30L);
        inv.confirmDeduction(30L);
        assertThat(inv.getTotalQuantity()).isEqualTo(70L);
        assertThat(inv.getLockedQuantity()).isZero();
        assertThat(inv.getAvailableQuantity()).isEqualTo(70L);
    }

    @Test
    @DisplayName("确认扣减超过锁定数量时拒绝")
    void confirmDeductionRejectsOverDeduction() {
        Inventory inv = Inventory.initialize(1L, 100L);
        inv.lock(30L);
        assertThatThrownBy(() -> inv.confirmDeduction(31L))
                .isInstanceOf(InventoryException.class);
    }
}
