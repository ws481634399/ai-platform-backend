package com.ai.mall.product.domain.category;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.product.domain.shared.ProductErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 分类业务异常：按错误语义绑定 HTTP 状态（不存在 404 / 冲突 409 / 规则拒绝 400）。
 */
public class CategoryException extends BusinessException {

    public CategoryException(ProductErrorCode errorCode, HttpStatus httpStatus) {
        super(errorCode, httpStatus);
    }

    public CategoryException(ProductErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }

    public static CategoryException notFound(long id) {
        return new CategoryException(ProductErrorCode.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND,
                "分类不存在: id=" + id);
    }

    public static CategoryException parentNotFound(long parentId) {
        return new CategoryException(ProductErrorCode.CATEGORY_PARENT_NOT_FOUND, HttpStatus.BAD_REQUEST,
                "父分类不存在: parentId=" + parentId);
    }

    public static CategoryException parentDisabled() {
        return new CategoryException(ProductErrorCode.CATEGORY_PARENT_DISABLED, HttpStatus.BAD_REQUEST);
    }

    public static CategoryException levelExceeded() {
        return new CategoryException(ProductErrorCode.CATEGORY_LEVEL_EXCEEDED, HttpStatus.BAD_REQUEST);
    }

    public static CategoryException selfReference() {
        return new CategoryException(ProductErrorCode.CATEGORY_SELF_REFERENCE, HttpStatus.BAD_REQUEST);
    }

    public static CategoryException cycle() {
        return new CategoryException(ProductErrorCode.CATEGORY_CYCLE, HttpStatus.BAD_REQUEST);
    }

    public static CategoryException nameDuplicated(String name) {
        return new CategoryException(ProductErrorCode.CATEGORY_NAME_DUPLICATED, HttpStatus.CONFLICT,
                "同级分类下名称已存在: " + name);
    }
}
