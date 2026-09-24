package com.ai.mall.order.interfaces.rest.internal;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.order.application.order.port.OrderCompensationPort;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 补偿登记内部端点（CHG-0025 M7 STORY-009-03-01）。
 *
 * <p>mall-inventory 消费者 confirm/release 调用失败时先登记补偿（再抛异常交 MQ 重试/DLQ），
 * 复用 M4 既有 OrderCompensationPort 幂等登记；经 ROLE_SERVICE + X-Internal-Token 保护。
 */
@RestController
@RequestMapping("/api/internal/compensations")
public class CompensationInternalController {

    private final OrderCompensationPort compensationPort;

    public CompensationInternalController(OrderCompensationPort compensationPort) {
        this.compensationPort = compensationPort;
    }

    @PostMapping
    public UnifyResult<String> register(@RequestBody CompensationRequest request) {
        List<OrderCompensationPort.InventoryLine> lines = request.lines().stream()
                .map(line -> new OrderCompensationPort.InventoryLine(line.skuId(), line.quantity(),
                        line.reservationId()))
                .toList();
        switch (request.type()) {
            case INVENTORY_CONFIRM_DEDUCT ->
                    compensationPort.enqueueInventoryConfirm(request.orderNo(), lines, request.reason());
            case INVENTORY_RELEASE ->
                    compensationPort.enqueueInventoryRelease(request.orderNo(), lines, request.reason());
        }
        return UnifyResult.ok("accepted");
    }

    /** 补偿登记请求。 */
    public record CompensationRequest(String orderId, String orderNo, CompensationType type, String reason,
                                      List<Line> lines) {
    }

    /** 补偿类型（库存两类）。 */
    public enum CompensationType {
        INVENTORY_CONFIRM_DEDUCT,
        INVENTORY_RELEASE
    }

    /** 补偿载荷中的库存行。 */
    public record Line(long skuId, int quantity, String reservationId) {
    }
}
