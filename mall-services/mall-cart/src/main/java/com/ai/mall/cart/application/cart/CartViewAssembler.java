package com.ai.mall.cart.application.cart;

import com.ai.mall.cart.application.cart.CartReadModel.CartLine;
import com.ai.mall.cart.application.cart.CartReadModel.CartView;
import com.ai.mall.cart.application.cart.CartReadModel.ItemStatus;
import com.ai.mall.cart.application.cart.CartReadModel.StockStatus;
import com.ai.mall.cart.application.cart.ProductSkuClient.SkuSnapshot;
import com.ai.mall.cart.domain.cart.CartConstants;
import com.ai.mall.cart.domain.cart.CartItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 购物车读模型装配器（CHG-0018 DU-BE-802）。
 *
 * <p>纯直线映射、无 IO、无 Spring 依赖，便于表驱动单测覆盖状态优先级矩阵：
 * <ol>
 *   <li>product 整体失败 → 所有条目 itemStatus=UNKNOWN（商品字段/最新价留空）；</li>
 *   <li>快照占位缺失（product/sku 已删）→ NOT_FOUND；productStatus≠ON_SALE → PRODUCT_OFF_SHELF；
 *       product 在售但 skuStatus≠ENABLED → SKU_INVALID；</li>
 *   <li>最新价存在且不等于 priceFenAtAdded → PRICE_CHANGED（其余可买性不变）；否则 VALID；</li>
 *   <li>库存 0 → OUT_OF_STOCK；1..9 → LOW_STOCK；≥10 → IN_STOCK；inventory 失败 → UNKNOWN。</li>
 * </ol>
 *
 * <p>合计口径（AC-013 / story-design §1）：仅 itemStatus=VALID 且 selected
 * 且 stockStatus 为 IN_STOCK/LOW_STOCK 的条目，按 product 最新价 × 数量累加整数分；
 * PRICE_CHANGED 条目不是 VALID，不参与合计（前端需用户确认新价后才重新纳入，M4 结算另算）。
 * 装配全程只读，不触发任何 Redis 写。
 */
public final class CartViewAssembler {

    /** product 双状态 SSOT 字符串（与 mall-product ProductStatus/SkuStatus 枚举名一致）。 */
    static final String PRODUCT_ON_SALE = "ON_SALE";
    static final String SKU_ENABLED = "ENABLED";

    private CartViewAssembler() {
    }

    /**
     * @param items            Redis 车条目（保持存储顺序）
     * @param snapshotsBySku   product sku/batch 结果（skuId → 快照）；productFailed=true 时忽略
     * @param availabilityBySku inventory availability 结果（skuId → 精确数量）；inventoryFailed=true 时忽略
     * @param productFailed    product 依赖是否故障（条目级商品态全 UNKNOWN）
     * @param inventoryFailed  inventory 依赖是否故障（库存态全 UNKNOWN）
     */
    public static CartView assemble(List<CartItem> items,
                                    Map<Long, SkuSnapshot> snapshotsBySku,
                                    Map<Long, Long> availabilityBySku,
                                    boolean productFailed,
                                    boolean inventoryFailed) {
        List<CartLine> lines = new ArrayList<>(items.size());
        long selectedTotalFen = 0L;
        int selectedCount = 0;
        for (CartItem item : items) {
            SkuSnapshot snapshot = snapshotsBySku == null ? null : snapshotsBySku.get(item.skuId());
            ItemStatus itemStatus = productFailed ? ItemStatus.UNKNOWN : resolveItemStatus(item, snapshot);
            StockStatus stockStatus = inventoryFailed
                    ? StockStatus.UNKNOWN
                    : resolveStockStatus(availabilityBySku == null ? null : availabilityBySku.get(item.skuId()));

            Long productId = snapshot == null ? null : snapshot.productId();
            Long priceFen = snapshot == null ? null : snapshot.salePriceInCents();
            CartLine line = new CartLine(
                    item.skuId(), item.quantity(), item.selected(),
                    productId,
                    snapshot == null ? null : snapshot.productName(),
                    // product 快照无独立 SKU 名称字段，以 skuCode 承载行内 SKU 标识
                    snapshot == null ? null : snapshot.skuCode(),
                    snapshot == null || snapshot.specifications() == null
                            ? Map.of() : snapshot.specifications(),
                    snapshot == null ? null : snapshot.mainImageUrl(),
                    priceFen, item.priceFenAtAdded(),
                    itemStatus, stockStatus,
                    item.createdAt(), item.updatedAt());
            lines.add(line);

            if (item.selected()) {
                selectedCount++;
                if (itemStatus == ItemStatus.VALID
                        && (stockStatus == StockStatus.IN_STOCK || stockStatus == StockStatus.LOW_STOCK)
                        && priceFen != null) {
                    selectedTotalFen += priceFen * item.quantity();
                }
            }
        }
        return new CartView(List.copyOf(lines), selectedTotalFen, selectedCount);
    }

    private static ItemStatus resolveItemStatus(CartItem item, SkuSnapshot snapshot) {
        // product batch 对已删商品/SKU 以 salable=false 且状态字段全 null 占位
        if (snapshot == null || (snapshot.productStatus() == null && snapshot.skuStatus() == null)) {
            return ItemStatus.NOT_FOUND;
        }
        if (!PRODUCT_ON_SALE.equals(snapshot.productStatus())) {
            return ItemStatus.PRODUCT_OFF_SHELF;
        }
        if (!SKU_ENABLED.equals(snapshot.skuStatus())) {
            return ItemStatus.SKU_INVALID;
        }
        Long latestPrice = snapshot.salePriceInCents();
        if (latestPrice != null && latestPrice != item.priceFenAtAdded()) {
            return ItemStatus.PRICE_CHANGED;
        }
        return ItemStatus.VALID;
    }

    private static StockStatus resolveStockStatus(Long availableQty) {
        if (availableQty == null) {
            // inventory 有返回但缺该 SKU（无库存记录）按 0 处理，与端点占位语义一致
            return StockStatus.OUT_OF_STOCK;
        }
        if (availableQty <= 0) {
            return StockStatus.OUT_OF_STOCK;
        }
        return availableQty < CartConstants.IN_STOCK_THRESHOLD
                ? StockStatus.LOW_STOCK : StockStatus.IN_STOCK;
    }
}
