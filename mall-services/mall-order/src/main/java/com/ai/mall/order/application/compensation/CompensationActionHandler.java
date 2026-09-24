package com.ai.mall.order.application.compensation;

import com.ai.mall.order.domain.compensation.CompensationTask;

/**
 * 补偿动作执行器（CHG-0025 STORY-009-05-01）。
 *
 * <p>CompensationService 调度时按 {@link #supports(String)} 选择执行器；
 * 每个执行器只负责一类 operation，执行失败直接抛异常由调度服务记有界退避。</p>
 */
public interface CompensationActionHandler {

    /** 是否支持该补偿操作。 */
    boolean supports(String operation);

    /** 执行一次补偿（下游操作自身幂等）。 */
    void handle(CompensationTask task);
}
