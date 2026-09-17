package com.ai.mall.order.application.compensation;

import java.util.List;

/**
 * 库存类补偿任务载荷（JSON 落 compensation_task.payload，CHG-0019 REQ-M4-004）。
 */
public record InventoryCompensationPayload(List<Line> lines) {

    /** 单个库存预留的补偿描述（reservationId 为库存侧幂等键）。 */
    public record Line(long skuId, int quantity, String reservationId) {
    }
}
