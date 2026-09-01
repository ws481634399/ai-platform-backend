package com.ai.mall.common.web.testsupport;

import jakarta.validation.constraints.NotBlank;

/**
 * 参数校验测试载体。
 */
public class ValidRequest {

    @NotBlank
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
