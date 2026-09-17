package com.ai.mall.order.application.order.port;

import java.util.List;

/**
 * mall-inventory 出站端口：可售查询 / 锁定 / 释放 / 确认扣减（CHG-0019）。
 *
 * <p>所有操作均按 reservationId 幂等（库存侧以 reservation 状态 CAS 保证）：
 * 重复 release/confirm 不产生第二次数量变化。传输故障由实现归一为 503，
 * 库存不足等业务拒绝携带订单域错误码抛出。
 */
public interface InventoryPort {

    /** 批量可售数量（无库存记录的 skuId 语义为 0）。 */
    List<Availability> availability(List<Long> skuIds);

    /** 锁定库存（reservationId 幂等；不足抛 B0404，依赖故障抛 503）。 */
    void lock(String reservationId, long skuId, long quantity);

    /** 释放预留（幂等；重复释放不重复增加库存）。 */
    void release(String reservationId);

    /** 确认扣减预留（幂等；重复确认不重复扣减）。 */
    void confirm(String reservationId);

    record Availability(long skuId, long availableQty) {
    }
}
