package com.ai.mall.product.interfaces.rest.internal.dto;

import com.ai.mall.common.web.annotation.StringId;
import java.util.Map;

/**
 * 商品快照视图（内部查询契约，不暴露领域实体）。
 *
 * <p>CHG-0015：业务 ID 同样输出字符串；消费端 Jackson 默认可将字符串回读为 long。
 */
public record ProductSnapshotView(
        @StringId long productId,
        @StringId long skuId,
        String productName,
        String skuName,
        Map<String, String> skuAttributes,
        long price,
        String image,
        String currentStatus
) {}
