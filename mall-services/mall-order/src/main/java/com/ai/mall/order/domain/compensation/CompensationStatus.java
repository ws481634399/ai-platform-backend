package com.ai.mall.order.domain.compensation;

/** 补偿任务状态（CHG-0019 REQ-M4-004）。 */
public enum CompensationStatus {

    /** 待执行（nextRetryAt 到期后被调度器捞取）。 */
    PENDING,
    /** 补偿成功（终态）。 */
    SUCCESS,
    /** 达到最大重试次数仍失败（终态，等待人工重试）。 */
    FAILED_DEAD
}
