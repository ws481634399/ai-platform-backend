package com.ai.mall.common.core.result;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UnifyResult 工厂单元测试：字段冻结、success 语义、traceId 装配。
 */
class UnifyResultTest {

    @AfterEach
    void tearDown() {
        com.ai.mall.common.core.trace.TraceContext.clear();
    }

    @Test
    void okWithDataShouldFillSuccessFields() {
        com.ai.mall.common.core.trace.TraceContext.set("t".repeat(32));
        UnifyResult<String> r = UnifyResult.ok("payload");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getCode()).isEqualTo("0");
        assertThat(r.getMessage()).isEqualTo("成功");
        assertThat(r.getData()).isEqualTo("payload");
        assertThat(r.getTraceId()).hasSize(32);
    }

    @Test
    void okWithoutDataShouldKeepNullData() {
        UnifyResult<Void> r = UnifyResult.ok();
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getData()).isNull();
    }

    @Test
    void failWithErrorCodeShouldUseDefaultMessage() {
        UnifyResult<Void> r = UnifyResult.fail(CommonErrorCode.BUSINESS_ERROR);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getCode()).isEqualTo("B0001");
        assertThat(r.getMessage()).isEqualTo("业务处理失败");
    }

    @Test
    void failWithCustomMessageShouldOverride() {
        UnifyResult<Void> r = UnifyResult.fail(CommonErrorCode.PARAM_INVALID, "name 不能为空");
        assertThat(r.getCode()).isEqualTo("A0001");
        assertThat(r.getMessage()).isEqualTo("name 不能为空");
    }

    @Test
    void traceIdShouldBeNullWhenContextEmpty() {
        UnifyResult<Void> r = UnifyResult.ok();
        assertThat(r.getTraceId()).isNull();
    }
}
