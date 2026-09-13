package com.ai.mall.inventory.domain.inventory;

import com.ai.mall.common.web.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * 库存域异常。
 */
public class InventoryException extends BusinessException {

    public InventoryException(InventoryErrorCode code, HttpStatus status) {
        super(code, status);
    }

    public InventoryException(InventoryErrorCode code, HttpStatus status, String message) {
        super(code, status, message);
    }

    public static InventoryException notFound(long skuId) {
        return new InventoryException(InventoryErrorCode.INVENTORY_NOT_FOUND, HttpStatus.NOT_FOUND,
                "库存不存在: skuId=" + skuId);
    }

    public static InventoryException alreadyExists(long skuId) {
        return new InventoryException(InventoryErrorCode.INVENTORY_ALREADY_EXISTS, HttpStatus.CONFLICT,
                "库存已存在: skuId=" + skuId);
    }

    public static InventoryException quantityInvalid(String message) {
        return new InventoryException(InventoryErrorCode.INVENTORY_QUANTITY_INVALID, HttpStatus.BAD_REQUEST, message);
    }

    public static InventoryException insufficient(long skuId) {
        return new InventoryException(InventoryErrorCode.INVENTORY_INSUFFICIENT, HttpStatus.CONFLICT,
                "可用库存不足: skuId=" + skuId);
    }

    public static InventoryException skuNotFound(long skuId) {
        return new InventoryException(InventoryErrorCode.SKU_NOT_FOUND, HttpStatus.BAD_REQUEST,
                "SKU 不存在: skuId=" + skuId);
    }

    public static InventoryException reservationNotFound(String reservationId) {
        return new InventoryException(InventoryErrorCode.RESERVATION_NOT_FOUND, HttpStatus.NOT_FOUND,
                "预留记录不存在: reservationId=" + reservationId);
    }

    public static InventoryException reservationInvalidState(String reservationId, ReservationStatus status) {
        return new InventoryException(InventoryErrorCode.RESERVATION_INVALID_STATE, HttpStatus.CONFLICT,
                "预留状态非法: reservationId=" + reservationId + ", status=" + status);
    }
}
