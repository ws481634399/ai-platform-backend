package com.ai.mall.order.application.order.port;

import java.util.List;

/**
 * 交易补偿登记端口（CHG-0019 REQ-M4-004）。
 *
 * <p>挂点：① 库存锁定成功但订单落库失败 → RELEASE；② 支付 CAS 成功后 confirm 失败 → CONFIRM；
 * ③ 取消 CAS 成功后逐行 release 失败 → RELEASE。登记幂等（同业务单同操作唯一），
 * 由补偿调度器按 30s/1m/2m/5m/10m 有界退避执行，5 次失败转 FAILED_DEAD 人工。
 */
public interface OrderCompensationPort {

    /** 登记/复用库存释放补偿任务。 */
    void enqueueInventoryRelease(String orderNo, List<InventoryLine> lines, String reason);

    /** 登记/复用库存确认扣减补偿任务。 */
    void enqueueInventoryConfirm(String orderNo, List<InventoryLine> lines, String reason);

    /** 补偿载荷中的库存行（reservationId 是库存侧幂等键）。 */
    record InventoryLine(long skuId, int quantity, String reservationId) {
    }
}
