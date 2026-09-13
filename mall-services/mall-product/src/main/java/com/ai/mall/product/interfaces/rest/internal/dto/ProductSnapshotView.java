package com.ai.mall.product.interfaces.rest.internal.dto;

import java.util.Map;

/**
 * 商品快照视图（内部查询契约，不暴露领域实体）。
 */
public record ProductSnapshotView(
        long productId,
        long skuId,
        String productName,
        String skuName,
        Map<String, String> skuAttributes,
        long price,
        String image,
        String currentStatus
) {}
