package com.ai.mall.order.application.order.event;

import com.ai.mall.order.domain.order.Order;

/**
 * 订单事件 Outbox flush 端口（application 层定义）。
 *
 * <p>由仓储在业务事务内调用：异步模式把聚合收集的事件写入 outbox_event（与业务数据同事务），
 * 同步模式取出丢弃。写失败抛异常触发业务事务回滚。
 */
public interface OrderEventOutbox {

    /** 在当前事务内 flush 聚合的待发事件；无事件为空操作。 */
    void flush(Order order);
}
