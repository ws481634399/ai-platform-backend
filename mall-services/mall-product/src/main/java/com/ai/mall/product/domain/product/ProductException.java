package com.ai.mall.product.domain.product;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.product.domain.shared.ProductErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 商品域异常。
 */
public class ProductException extends BusinessException {

    public ProductException(ProductErrorCode code, HttpStatus status) {
        super(code, status);
    }

    public ProductException(ProductErrorCode code, HttpStatus status, String message) {
        super(code, status, message);
    }

    public static ProductException notFound(long id) {
        return new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "商品不存在: id=" + id);
    }

    public static ProductException codeDuplicated(String code) {
        return new ProductException(ProductErrorCode.PRODUCT_CODE_DUPLICATED, HttpStatus.CONFLICT,
                "商品编码已存在: " + code);
    }

    public static ProductException categoryInvalid(long categoryId) {
        return new ProductException(ProductErrorCode.PRODUCT_CATEGORY_INVALID, HttpStatus.BAD_REQUEST,
                "分类不存在或已禁用: id=" + categoryId);
    }

    public static ProductException brandInvalid(long brandId) {
        return new ProductException(ProductErrorCode.PRODUCT_BRAND_INVALID, HttpStatus.BAD_REQUEST,
                "品牌不存在或已禁用: id=" + brandId);
    }

    public static ProductException mainImageDuplicated() {
        return new ProductException(ProductErrorCode.PRODUCT_MAIN_IMAGE_DUPLICATED, HttpStatus.BAD_REQUEST,
                "一个商品仅能有一张主图");
    }

    public static ProductException skuCodeDuplicated(String skuCode) {
        return new ProductException(ProductErrorCode.SKU_CODE_DUPLICATED, HttpStatus.CONFLICT,
                "SKU 编码已存在: " + skuCode);
    }

    public static ProductException skuSpecDuplicated() {
        return new ProductException(ProductErrorCode.SKU_SPEC_DUPLICATED, HttpStatus.CONFLICT,
                "SKU 规格组合已存在");
    }

    public static ProductException skuNotFound(long skuId) {
        return new ProductException(ProductErrorCode.SKU_NOT_FOUND, HttpStatus.NOT_FOUND,
                "SKU 不存在: id=" + skuId);
    }

    public static ProductException negativePrice() {
        return new ProductException(ProductErrorCode.SKU_PRICE_INVALID, HttpStatus.BAD_REQUEST,
                "SKU 价格不能为负");
    }
}
