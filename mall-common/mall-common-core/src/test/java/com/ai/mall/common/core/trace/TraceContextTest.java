package com.ai.mall.common.core.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TraceContext 单元测试：生成格式、读写清理、线程隔离。
 */
class TraceContextTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void generateShouldReturn32LowercaseHex() {
        String traceId = TraceContext.generate();
        assertThat(traceId).hasSize(32).matches("^[0-9a-f]{32}$");
    }

    @Test
    void generateShouldReturnUniqueIds() {
        assertThat(TraceContext.generate()).isNotEqualTo(TraceContext.generate());
    }

    @Test
    void setThenGetShouldReturnValue() {
        TraceContext.set("abc123");
        assertThat(TraceContext.get()).isEqualTo("abc123");
    }

    @Test
    void getShouldReturnNullWhenNotSet() {
        assertThat(TraceContext.get()).isNull();
    }

    @Test
    void clearShouldRemoveValue() {
        TraceContext.set("abc123");
        TraceContext.clear();
        assertThat(TraceContext.get()).isNull();
    }

    @Test
    void shouldIsolateBetweenThreads() throws Exception {
        TraceContext.set("main-thread-id");
        String[] childValue = new String[1];
        Thread child = new Thread(() -> childValue[0] = TraceContext.get());
        child.start();
        child.join();
        // 子线程未设置，应为 null（ThreadLocal 隔离）
        assertThat(childValue[0]).isNull();
        assertThat(TraceContext.get()).isEqualTo("main-thread-id");
    }
}
