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
    BRAND_NAME_DUPLICATED("B2122", "品牌名称已存在"),

    PRODUCT_NOT_FOUND("B2141", "商品不存在"),
    PRODUCT_CODE_DUPLICATED("B2142", "商品编码已存在"),
    PRODUCT_CATEGORY_INVALID("B2143", "分类不存在或已禁用"),
    PRODUCT_BRAND_INVALID("B2144", "品牌不存在或已禁用"),
    PRODUCT_CATEGORY_DISABLED("B2145", "分类已禁用"),
    PRODUCT_BRAND_DISABLED("B2146", "品牌已禁用"),
    PRODUCT_MAIN_IMAGE_DUPLICATED("B2147", "一个商品仅能有一张主图"),
    PRODUCT_NOT_DRAFT("B2148", "仅 DRAFT 状态商品可修改"),
    PRODUCT_INVALID_STATUS_TRANSITION("B2149", "非法商品状态流转"),
    PRODUCT_PUBLISH_VALIDATION_FAILED("B2150", "商品不满足上架条件"),
    PRODUCT_ALREADY_ON_SALE("B2151", "商品已上架"),
    PRODUCT_NOT_ON_SALE("B2152", "商品未上架，无法下架"),

    SKU_NOT_FOUND("B2161", "SKU 不存在"),
    SKU_CODE_DUPLICATED("B2162", "SKU 编码已存在"),
    SKU_SPEC_DUPLICATED("B2163", "同商品下规格组合已存在"),
    SKU_PRICE_INVALID("B2164", "SKU 售价不能为负"),
    SKU_SPEC_EMPTY("B2165", "SKU 规格不能为空"),

    AVAILABILITY_BATCH_INVALID("B2181", "SKU 可售批量查询参数非法");

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
