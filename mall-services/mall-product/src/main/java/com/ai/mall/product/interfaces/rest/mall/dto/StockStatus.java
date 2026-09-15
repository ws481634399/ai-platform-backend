package com.ai.mall.product.interfaces.rest.mall.dto;

/**
 * SKU 可售状态三态（公开）：浏览器不接触精确库存。
 *
 * <p>阈值 SSOT：{@link #STOCK_IN_THRESHOLD}=10。
 * <ul>
 *   <li>available=0 → {@link #OUT_OF_STOCK}</li>
 *   <li>1 ≤ available < 10 → {@link #LOW_STOCK}</li>
 *   <li>available ≥ 10 → {@link #IN_STOCK}</li>
 *   <li>inventory 故障/超时 → {@link #UNKNOWN}（降级，HTTP 仍 200）</li>
 * </ul>
 */
public enum StockStatus {

    OUT_OF_STOCK,
    LOW_STOCK,
    IN_STOCK,
    UNKNOWN;

    /** 有货阈值：available ≥ 10 为 IN_STOCK。 */
    public static final long STOCK_IN_THRESHOLD = 10L;

    /**
     * 由精确可售数量映射三态。
     */
    public static StockStatus fromAvailableQty(long availableQty) {
        if (availableQty <= 0) {
            return OUT_OF_STOCK;
        }
        if (availableQty < STOCK_IN_THRESHOLD) {
            return LOW_STOCK;
        }
        return IN_STOCK;
    }
}
