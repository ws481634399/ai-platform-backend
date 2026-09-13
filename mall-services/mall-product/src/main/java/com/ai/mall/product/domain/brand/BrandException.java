package com.ai.mall.product.domain.brand;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.product.domain.shared.ProductErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 品牌业务异常：不存在 404 / 名称冲突 409。
 */
public class BrandException extends BusinessException {

    public BrandException(ProductErrorCode errorCode, HttpStatus httpStatus) {
        super(errorCode, httpStatus);
    }

    public BrandException(ProductErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }

    public static BrandException notFound(long id) {
        return new BrandException(ProductErrorCode.BRAND_NOT_FOUND, HttpStatus.NOT_FOUND,
                "品牌不存在: id=" + id);
    }

    public static BrandException nameDuplicated(String name) {
        return new BrandException(ProductErrorCode.BRAND_NAME_DUPLICATED, HttpStatus.CONFLICT,
                "品牌名称已存在: " + name);
    }
}
