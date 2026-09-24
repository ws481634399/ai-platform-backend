package com.ai.mall.order.interfaces.rest.admin.dto;

import com.ai.mall.common.web.annotation.StringId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * 管理端订单/补偿接口契约（CHG-0019，/api/admin）。
 */
public final class AdminOrderDtos {

    private AdminOrderDtos() {
    }

    /** 发货请求。 */
    public record ShipRequest(
            @NotBlank(message = "物流公司不能为空")
            @Size(max = 64) String deliveryCompany,
            @NotBlank(message = "运单号不能为空")
            @Size(max = 64) String trackingNo) {
    }

    /** 补偿任务视图。 */
    public record CompensationView(@StringId long id, String businessType, String businessId, String operation,
                                   String payload,
                                   String status, int retryCount, int maxRetries, String lastError,
                                   Instant nextRetryAt, Instant createdAt, Instant updatedAt,
                                   String traceId) {
    }
}
