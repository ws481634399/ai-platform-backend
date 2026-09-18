package com.ai.mall.product.application.search;

/**
 * 商品搜索变更事件（CHG-0021）：商品写操作事务提交后发出。
 *
 * <p>不携带 payload——监听器在 AFTER_COMMIT 阶段重查当前投影，
 * 在售则 POST 投影、不可见则 DELETE，彻底规避长事务内旧值与乱序问题。
 */
public record ProductSearchChangedEvent(long productId, String operation) {

    public static ProductSearchChangedEvent of(long productId, String operation) {
        return new ProductSearchChangedEvent(productId, operation);
    }
}
