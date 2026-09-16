package com.ai.mall.cart.application.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.mall.cart.application.cart.CartReadModel.CartLine;
import com.ai.mall.cart.application.cart.CartReadModel.CartView;
import com.ai.mall.cart.application.cart.CartReadModel.ItemStatus;
import com.ai.mall.cart.application.cart.CartReadModel.StockStatus;
import com.ai.mall.cart.application.cart.ProductSkuClient.SkuSnapshot;
import com.ai.mall.cart.domain.cart.CartItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CartViewAssembler 表驱动单测（CHG-0018 DU-BE-802）：
 * 双状态优先级矩阵、调价、库存阈值（0/1/9/10 边界）、降级与选中合计排除规则。
 */
class CartViewAssemblerTest {

    private static CartItem item(long skuId, int qty, boolean selected, long priceAtAdded) {
        return new CartItem(skuId, qty, selected, priceAtAdded, "2026-09-16T01:00:00Z", "2026-09-16T01:00:00Z");
    }

    private static SkuSnapshot snapshot(long skuId, String productStatus, String skuStatus, Long price) {
        return new SkuSnapshot(9001L, "演示手机", productStatus, skuId, "SKU-" + skuId,
                skuStatus, price, "https://cdn.example.com/" + skuId + ".png",
                Map.of("颜色", "黑"), productStatus != null && "ON_SALE".equals(productStatus)
                        && "ENABLED".equals(skuStatus));
    }

    private static SkuSnapshot missing(long skuId) {
        return new SkuSnapshot(null, null, null, skuId, null, null,
                null, null, Map.of(), false);
    }

    @Test
    @DisplayName("空条目 → 空视图 0/0")
    void emptyCart() {
        CartView view = CartViewAssembler.assemble(List.of(), Map.of(), Map.of(), false, false);
        assertThat(view.items()).isEmpty();
        assertThat(view.selectedTotalFen()).isZero();
        assertThat(view.selectedCount()).isZero();
    }

    @Test
    @DisplayName("AC-008 VALID 条目回填图/名/规格/最新价/状态与选择")
    void validLineEnriched() {
        CartView view = CartViewAssembler.assemble(
                List.of(item(1001, 2, true, 39900)),
                Map.of(1001L, snapshot(1001, "ON_SALE", "ENABLED", 39900L)),
                Map.of(1001L, 20L), false, false);
        CartLine line = view.items().get(0);
        assertThat(line.itemStatus()).isEqualTo(ItemStatus.VALID);
        assertThat(line.stockStatus()).isEqualTo(StockStatus.IN_STOCK);
        assertThat(line.productId()).isEqualTo(9001L);
        assertThat(line.productName()).isEqualTo("演示手机");
        assertThat(line.skuName()).isEqualTo("SKU-1001");
        assertThat(line.specs()).containsEntry("颜色", "黑");
        assertThat(line.imageUrl()).endsWith("1001.png");
        assertThat(line.priceFen()).isEqualTo(39900);
        assertThat(view.selectedTotalFen()).isEqualTo(39900L * 2);
        assertThat(view.selectedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-009 下架→PRODUCT_OFF_SHELF；SKU 禁用→SKU_INVALID；缺失占位→NOT_FOUND；快照 Map 缺项同 NOT_FOUND")
    void invalidStatusMatrix() {
        CartView view = CartViewAssembler.assemble(
                List.of(item(1, 1, true, 100), item(2, 1, true, 100),
                        item(3, 1, true, 100), item(4, 1, true, 100)),
                Map.of(1L, snapshot(1, "OFF_SHELF", "ENABLED", 100L),
                        2L, snapshot(2, "ON_SALE", "DISABLED", 100L),
                        3L, missing(3)),
                Map.of(1L, 50L, 2L, 50L, 3L, 50L, 4L, 50L), false, false);
        assertThat(view.items()).extracting(CartLine::itemStatus)
                .containsExactly(ItemStatus.PRODUCT_OFF_SHELF, ItemStatus.SKU_INVALID,
                        ItemStatus.NOT_FOUND, ItemStatus.NOT_FOUND);
        assertThat(view.selectedTotalFen()).isZero();
    }

    @Test
    @DisplayName("AC-010 最新价与快照价不同 → PRICE_CHANGED，priceFen 展示新价；非 VALID 不参与合计")
    void priceChanged() {
        CartView view = CartViewAssembler.assemble(
                List.of(item(1001, 2, true, 39900)),
                Map.of(1001L, snapshot(1001, "ON_SALE", "ENABLED", 29900L)),
                Map.of(1001L, 20L), false, false);
        CartLine line = view.items().get(0);
        assertThat(line.itemStatus()).isEqualTo(ItemStatus.PRICE_CHANGED);
        assertThat(line.priceFen()).isEqualTo(29900);
        assertThat(line.priceFenAtAdded()).isEqualTo(39900);
        // AC-013：合计仅 VALID，PRICE_CHANGED 不纳入
        assertThat(view.selectedTotalFen()).isZero();
    }

    @Test
    @DisplayName("AC-011 库存阈值 0/1/9/10 边界 → OUT/LOW/LOW/IN")
    void stockThresholds() {
        List<CartItem> items = List.of(item(1, 1, true, 100), item(2, 1, true, 100),
                item(3, 1, true, 100), item(4, 1, true, 100));
        Map<Long, SkuSnapshot> snaps = Map.of(
                1L, snapshot(1, "ON_SALE", "ENABLED", 100L),
                2L, snapshot(2, "ON_SALE", "ENABLED", 100L),
                3L, snapshot(3, "ON_SALE", "ENABLED", 100L),
                4L, snapshot(4, "ON_SALE", "ENABLED", 100L));
        CartView view = CartViewAssembler.assemble(items, snaps,
                Map.of(1L, 0L, 2L, 1L, 3L, 9L, 4L, 10L), false, false);
        assertThat(view.items()).extracting(CartLine::stockStatus)
                .containsExactly(StockStatus.OUT_OF_STOCK, StockStatus.LOW_STOCK,
                        StockStatus.LOW_STOCK, StockStatus.IN_STOCK);
        // 9 件与 10 件两条 LOW/IN 纳入合计（缺货排除）：100×1 + 100×1 + 100×1
        assertThat(view.selectedTotalFen()).isEqualTo(300);
    }

    @Test
    @DisplayName("availability 缺该 SKU（无库存记录）按 0 → OUT_OF_STOCK")
    void missingAvailabilityTreatedZero() {
        CartView view = CartViewAssembler.assemble(
                List.of(item(1001, 1, true, 100)),
                Map.of(1001L, snapshot(1001, "ON_SALE", "ENABLED", 100L)),
                Map.of(), false, false);
        assertThat(view.items().get(0).stockStatus()).isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(view.selectedTotalFen()).isZero();
    }

    @Test
    @DisplayName("AC-012 product 故障：全部 itemStatus=UNKNOWN、商品字段/最新价为空，但库存态照常、整车可装配")
    void productFailureDegradesItemStatus() {
        CartView view = CartViewAssembler.assemble(
                List.of(item(1001, 2, true, 39900)),
                Map.of(), Map.of(1001L, 20L), true, false);
        CartLine line = view.items().get(0);
        assertThat(line.itemStatus()).isEqualTo(ItemStatus.UNKNOWN);
        assertThat(line.stockStatus()).isEqualTo(StockStatus.IN_STOCK);
        assertThat(line.productName()).isNull();
        assertThat(line.priceFen()).isNull();
        assertThat(line.priceFenAtAdded()).isEqualTo(39900);
        assertThat(line.quantity()).isEqualTo(2);
        assertThat(view.selectedTotalFen()).isZero();
        assertThat(view.selectedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-012 inventory 故障：stockStatus=UNKNOWN，商品态 VALID 不受影响；UNKNOWN 库存排除合计")
    void inventoryFailureDegradesStockOnly() {
        CartView view = CartViewAssembler.assemble(
                List.of(item(1001, 2, true, 100)),
                Map.of(1001L, snapshot(1001, "ON_SALE", "ENABLED", 100L)),
                Map.of(), false, true);
        CartLine line = view.items().get(0);
        assertThat(line.itemStatus()).isEqualTo(ItemStatus.VALID);
        assertThat(line.stockStatus()).isEqualTo(StockStatus.UNKNOWN);
        assertThat(view.selectedTotalFen()).isZero();
    }

    @Test
    @DisplayName("AC-013 未选中条目不进合计；selectedCount 仍按勾选标记统计失效条目")
    void unselectedAndInvalidExcluded() {
        CartView view = CartViewAssembler.assemble(
                List.of(item(1, 2, false, 100), item(2, 3, true, 100)),
                Map.of(1L, snapshot(1, "ON_SALE", "ENABLED", 100L),
                        2L, missing(2)),
                Map.of(1L, 20L, 2L, 20L), false, false);
        assertThat(view.selectedTotalFen()).isZero();
        assertThat(view.selectedCount()).isEqualTo(1);
        assertThat(view.items().get(0).stockStatus()).isEqualTo(StockStatus.IN_STOCK);
    }

    @Test
    @DisplayName("双依赖同时故障：双 UNKNOWN，车条目数量/选择/快照价仍展示")
    void bothDependenciesDown() {
        CartView view = CartViewAssembler.assemble(
                List.of(item(1001, 7, true, 12345)),
                Map.of(), Map.of(), true, true);
        CartLine line = view.items().get(0);
        assertThat(line.itemStatus()).isEqualTo(ItemStatus.UNKNOWN);
        assertThat(line.stockStatus()).isEqualTo(StockStatus.UNKNOWN);
        assertThat(line.quantity()).isEqualTo(7);
        assertThat(line.priceFenAtAdded()).isEqualTo(12345);
        assertThat(view.selectedTotalFen()).isZero();
        assertThat(view.selectedCount()).isEqualTo(1);
    }
}
