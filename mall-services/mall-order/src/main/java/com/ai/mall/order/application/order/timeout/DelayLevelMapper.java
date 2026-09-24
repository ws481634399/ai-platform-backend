package com.ai.mall.order.application.order.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 支付超时分钟数 → RocketMQ 延迟级别映射器（CHG-0025 STORY-009-04-01）。
 *
 * <p>RocketMQ 开源版 18 个固定延迟级别（秒）：
 * 1/5/10/30、60/120/180/240/300/360/420/480/540/600/1200/1800/3600/7200。
 * 映射策略为<b>向上对齐</b>：取 ≥ 目标时长的最小级别，保证消息不早于 expireAt 到期；
 * 目标超过最大级别（2h）封顶 level 18 尽力而为；超时分钟数 ≤0 视为非法配置回退 level 16（30 分钟）。</p>
 */
@Component
public class DelayLevelMapper {

    private static final Logger log = LoggerFactory.getLogger(DelayLevelMapper.class);

    /** 18 级对应的延迟秒数（下标 i 即 level i+1 的时长）。 */
    private static final int[] LEVEL_SECONDS = {
            1, 5, 10, 30,
            60, 120, 180, 240, 300, 360, 420, 480, 540, 600,
            1200, 1800, 3600, 7200
    };

    /** 非法超时回退级别：30 分钟（level 16）。 */
    private static final int FALLBACK_LEVEL = 16;
    /** 最大延迟级别。 */
    private static final int MAX_LEVEL = 18;

    private final int forceLevel;

    public DelayLevelMapper(@Value("${mall.order.delay.force-level:0}") int forceLevel) {
        this.forceLevel = forceLevel;
    }

    /**
     * @param timeoutMinutes 支付超时分钟数
     * @return RocketMQ 延迟级别（1~18）
     */
    public int toLevel(long timeoutMinutes) {
        if (forceLevel > 0) {
            return forceLevel;
        }
        if (timeoutMinutes <= 0) {
            log.warn("支付超时分钟数非法，回退默认级别 level={}, timeoutMinutes={}", FALLBACK_LEVEL, timeoutMinutes);
            return FALLBACK_LEVEL;
        }
        long targetSeconds = timeoutMinutes * 60L;
        for (int i = 0; i < LEVEL_SECONDS.length; i++) {
            if (LEVEL_SECONDS[i] >= targetSeconds) {
                return i + 1;
            }
        }
        log.warn("支付超时超过最大延迟级别 2h，封顶 level={}, timeoutMinutes={}", MAX_LEVEL, timeoutMinutes);
        return MAX_LEVEL;
    }
}
