package com.ai.mall.product.domain.product.event;

/**
 * 商品下架事件。
 */
public record ProductUnpublishedDomainEvent(long productId) implements ProductDomainEvent {
}
