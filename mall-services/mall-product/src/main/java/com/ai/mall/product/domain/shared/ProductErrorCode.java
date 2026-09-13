package com.ai.mall.product.domain.shared;

import com.ai.mall.common.core.result.ErrorCode;

/**
 * 商品域错误码（B21xx：分类 / 品牌主数据）。
 */
public enum ProductErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND("B2101", "分类不存在"),
    CATEGORY_NAME_DUPLICATED("B2102", "同级分类下名称已存在"),
    CATEGORY_PARENT_NOT_FOUND("B2103", "父分类不存在"),
    CATEGORY_PARENT_DISABLED("B2104", "父分类已禁用，不能在其下新增或移动子分类"),
    CATEGORY_LEVEL_EXCEEDED("B2105", "分类最多支持 3 级"),
    CATEGORY_SELF_REFERENCE("B2106", "不能将分类挂载到自身下"),
    CATEGORY_CYCLE("B2107", "不能将分类挂载到自己的子分类下"),

    BRAND_NOT_FOUND("B2121", "品牌不存在"),
    BRAND_NAME_DUPLICATED("B2122", "品牌名称已存在");

    private final String code;
    private final String message;

    ProductErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
