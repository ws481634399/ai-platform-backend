package com.ai.mall.common.core.result;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误码分段单元测试：0/A/B/S 段归属与唯一性。
 */
class CommonErrorCodeTest {

    @Test
    void successShouldUseZeroSegment() {
        assertThat(CommonErrorCode.SUCCESS.getCode()).isEqualTo("0");
    }

    @Test
    void paramErrorsShouldUseASegment() {
        assertThat(CommonErrorCode.PARAM_INVALID.getCode()).startsWith("A");
    }

    @Test
    void businessErrorsShouldUseBSegment() {
        assertThat(CommonErrorCode.BUSINESS_ERROR.getCode()).startsWith("B");
    }

    @Test
    void systemErrorsShouldUseSSegment() {
        assertThat(CommonErrorCode.SYSTEM_ERROR.getCode()).startsWith("S");
    }

    @Test
    void codesShouldBeUnique() {
        assertThat(CommonErrorCode.values())
                .extracting(CommonErrorCode::getCode)
                .doesNotHaveDuplicates();
    }

    @Test
    void messagesShouldNotBeBlank() {
        assertThat(CommonErrorCode.values())
                .extracting(CommonErrorCode::getMessage)
                .allSatisfy(m -> assertThat(m).isNotBlank());
    }
}
