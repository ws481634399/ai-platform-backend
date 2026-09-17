package com.ai.mall.order.domain.order;

/**
 * 订单业务单号生成端口（CHG-0019）。
 *
 * <p>orderNo 全局唯一、不暴露数据库自增、可作为跨服务业务引用；
 * 高并发下由实现保证不重复（撞唯一键时应用层最多重试 3 次）。
 */
public interface OrderNoGenerator {

    String next();
}
