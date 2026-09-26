package com.ai.mall.common.mq.consumer;

/**
 * 消费幂等组件（先占位后处理语义）。
 * <p>幂等键=eventId+consumerGroup（唯一键兜底并发）；占位-处理-回写三段式：</p>
 * <ul>
 *   <li>tryConsume：INSERT IGNORE 占位，首占返回 FIRST_PROCESSED，已存在返回 DUPLICATE</li>
 *   <li>markResult：业务处理成功后回写 SUCCESS；乱序跳过回写 SKIPPED</li>
 *   <li>deletePlaceholder：处理异常时删除占位，允许 RocketMQ 重试后再次处理</li>
 * </ul>
 */
public interface IdempotentConsumer {

    /**
     * 尝试占位消费。DB 异常直接上抛（fail-closed，禁止降级为无幂等消费）。
     */
    TryResult tryConsume(String eventId, String consumerGroup, String eventType,
                         String aggregateId, String traceId);

    /** 回写处理结果（SUCCESS/SKIPPED） */
    void markResult(String eventId, String consumerGroup, Result result);

    /**
     * 处理链收尾条件回写：仅当占位仍为 PROCESSING 时置 SUCCESS。
     * handler 已显式回写 SKIPPED（乱序裁决）时不得覆盖。
     */
    void markSuccessIfProcessing(String eventId, String consumerGroup);

    /** 删除占位（异常回滚场景，允许重试再处理） */
    void deletePlaceholder(String eventId, String consumerGroup);

    enum TryResult {
        /** 首次占位成功，调用方继续执行业务处理 */
        FIRST_PROCESSED,
        /** 已被占位/处理过，重复消息直接 ACK 跳过 */
        DUPLICATE
    }

    enum Result {
        SUCCESS, SKIPPED
    }
}
