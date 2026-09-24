package com.ai.mall.order.application.order.timeout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DelayLevelMapper 单测（TC-001）：超时分钟数 → RocketMQ 延迟级别（向上对齐/封顶/非法回退/force 覆盖）。 */
class DelayLevelMapperTest {

    private final DelayLevelMapper mapper = new DelayLevelMapper(0);

    @Test
    @DisplayName("恰好命中级别：1m→5、2m→6、3m→7、5m→9、10m→14、20m→15、30m→16、60m→17、120m→18")
    void exactMatches() {
        assertThat(mapper.toLevel(1)).isEqualTo(5);
        assertThat(mapper.toLevel(2)).isEqualTo(6);
        assertThat(mapper.toLevel(3)).isEqualTo(7);
        assertThat(mapper.toLevel(5)).isEqualTo(9);
        assertThat(mapper.toLevel(10)).isEqualTo(14);
        assertThat(mapper.toLevel(20)).isEqualTo(15);
        assertThat(mapper.toLevel(30)).isEqualTo(16);
        assertThat(mapper.toLevel(60)).isEqualTo(17);
        assertThat(mapper.toLevel(120)).isEqualTo(18);
    }

    @Test
    @DisplayName("向上对齐：非整级取 ≥ 目标时长的最小级别（45m→17=3600s、90m→18=7200s、4m→8=240s）")
    void ceilToNextLevel() {
        assertThat(mapper.toLevel(4)).isEqualTo(8);
        assertThat(mapper.toLevel(45)).isEqualTo(17);
        assertThat(mapper.toLevel(90)).isEqualTo(18);
    }

    @Test
    @DisplayName("超过最大级别时长时封顶 level 18（200m）")
    void capAtMaxLevel() {
        assertThat(mapper.toLevel(200)).isEqualTo(18);
    }

    @Test
    @DisplayName("非法超时（≤0）回退 level 16（30 分钟默认语义）")
    void illegalTimeoutFallsBack() {
        assertThat(mapper.toLevel(0)).isEqualTo(16);
        assertThat(mapper.toLevel(-5)).isEqualTo(16);
    }

    @Test
    @DisplayName("forceLevel 非 0 时强制该级别，忽略映射（集成测试短延迟用）")
    void forceLevelOverrides() {
        DelayLevelMapper forced = new DelayLevelMapper(2);
        assertThat(forced.toLevel(30)).isEqualTo(2);
        assertThat(forced.toLevel(200)).isEqualTo(2);
    }
}
