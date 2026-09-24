package com.ai.mall.order.application.compensation;

import com.ai.mall.order.application.order.port.InventoryPort;
import com.ai.mall.order.domain.compensation.CompensationTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 库存补偿执行器（CHG-0019 REQ-M4-004）：按 reservationId 幂等释放/确认扣减。
 *
 * <p>库存侧 release/confirm 以 reservation 状态 CAS 幂等，重复执行不会产生第二次数量变化。
 */
@Component
public class InventoryCompensationHandler implements CompensationActionHandler {

    private final InventoryPort inventoryPort;
    private final ObjectMapper objectMapper;

    public InventoryCompensationHandler(InventoryPort inventoryPort, ObjectMapper objectMapper) {
        this.inventoryPort = inventoryPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String operation) {
        return CompensationTask.OP_RELEASE_INVENTORY.equals(operation)
                || CompensationTask.OP_CONFIRM_INVENTORY.equals(operation);
    }

    /** 执行一次补偿；任一行失败抛异常由调度服务记退避。 */
    @Override
    public void handle(CompensationTask task) {
        InventoryCompensationPayload payload;
        try {
            payload = objectMapper.readValue(task.payload(), InventoryCompensationPayload.class);
        } catch (Exception ex) {
            // 载荷不可解析：重试无意义，但仍走有界重试（人工可见 FAILED_DEAD 与错误信息）
            throw new IllegalStateException("补偿载荷解析失败: " + ex.getMessage(), ex);
        }
        if (payload.lines() == null || payload.lines().isEmpty()) {
            return;
        }
        for (InventoryCompensationPayload.Line line : payload.lines()) {
            if (CompensationTask.OP_RELEASE_INVENTORY.equals(task.operation())) {
                inventoryPort.release(line.reservationId());
            } else if (CompensationTask.OP_CONFIRM_INVENTORY.equals(task.operation())) {
                inventoryPort.confirm(line.reservationId());
            } else {
                throw new IllegalStateException("不支持的补偿操作: " + task.operation());
            }
        }
    }
}
