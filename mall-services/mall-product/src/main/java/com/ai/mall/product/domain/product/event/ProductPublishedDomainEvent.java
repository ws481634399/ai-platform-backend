package com.ai.mall.product.domain.product.event;

/**
 * 商品上架事件。
 */
public record ProductPublishedDomainEvent(long productId) implements ProductDomainEvent {
}
