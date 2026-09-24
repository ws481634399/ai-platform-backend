package com.ai.mall.order.interfaces.rest.admin.dto;

import java.time.Instant;

/**
 * 延迟取消任务管理端 DTO（STORY-009-04-01）。
 */
public final class DelayTaskAdminDtos {

    /**
     * 延迟任务视图。
     *
     * @param orderId FAILED 源订单已消失时为 0（orderNo 回退显示 aggregateId）
     */
    public record DelayTaskView(long orderId, String orderNo, String delayStatus,
                                Instant createdAt, Instant cancelledAt, String lastError) {
    }

    private DelayTaskAdminDtos() {
    }
}
