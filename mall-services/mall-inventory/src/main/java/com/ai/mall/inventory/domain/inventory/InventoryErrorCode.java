package com.ai.mall.inventory.domain.inventory;

import com.ai.mall.common.core.result.ErrorCode;

/**
 * 库存域错误码（B22xx：库存核心能力）。
 */
public enum InventoryErrorCode implements ErrorCode {

    INVENTORY_NOT_FOUND("B2201", "库存不存在"),
    INVENTORY_ALREADY_EXISTS("B2202", "库存已存在"),
    INVENTORY_QUANTITY_INVALID("B2203", "库存数量非法"),
    INVENTORY_INSUFFICIENT("B2204", "可用库存不足"),
    SKU_NOT_FOUND("B2205", "SKU 不存在"),
    RESERVATION_NOT_FOUND("B2206", "预留记录不存在"),
    RESERVATION_INVALID_STATE("B2207", "预留状态非法"),
    AVAILABILITY_BATCH_INVALID("B2208", "可售批量查询参数非法");

    private final String code;
    private final String message;

    InventoryErrorCode(String code, String message) {
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
