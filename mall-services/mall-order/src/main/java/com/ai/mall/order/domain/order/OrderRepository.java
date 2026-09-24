package com.ai.mall.order.domain.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 订单仓储端口（CHG-0019）。
 *
 * <p>状态迁移唯一持久化路径 {@link #transition}：动作专属 CAS SQL（status+version 条件更新）
 * 与状态历史插入在同一事务；CAS rows=0 表示并发竞争失败，由应用层重读订单解读。
 */
public interface OrderRepository {

    /** 新建订单：orders + order_item + CREATE history 同事务插入，回填雪花主键。 */
    void insert(Order order);

    /** 按业务单号加载完整聚合（含商品行与状态历史）。 */
    Optional<Order> findByOrderNo(String orderNo);

    /** 内部裁决用：按主键仅取订单状态（不重建整聚合）。 */
    Optional<OrderStatus> findStatusById(long orderId);

    /** 双层幂等第二道：按会员 + 已消费 submitToken 反查首单。 */
    Optional<Order> findByMemberAndSubmitToken(long memberId, String submitToken);

    /**
     * CAS 状态迁移 + 同事务插历史。
     *
     * @return true 表示条件更新命中（产生一次真实迁移）；false 表示 status/version 不匹配（竞争或重复）
     */
    boolean transition(StatusTransition change);

    /** 分页查询订单头（不含商品行；商品行由 {@link #findItemsByOrderIds} 批量装配）。 */
    OrderPage page(OrderPageQuery query);

    /** 按一批订单 id 批量取商品行（避免列表 N+1）。 */
    List<OrderItem> findItemsByOrderIds(List<Long> orderIds);

    /** 状态迁移命令（from/expectedVersion 为 CAS 条件，其余为动作专属落库字段）。 */
    record StatusTransition(long orderId, String orderNo, long expectedVersion, OrderOperation operation,
                            OrderStatus from, OrderStatus to, String operator, String reason,
                            String deliveryCompany, String trackingNo, Instant occurredAt, Order order) {

        public static StatusTransition of(Order order, OrderOperation operation, OrderStatus to, String operator,
                                          String reason, String deliveryCompany, String trackingNo, Instant now) {
            return new StatusTransition(order.getId(), order.orderNo(), order.version(), operation,
                    order.status(), to, operator, reason, deliveryCompany, trackingNo, now, order);
        }
    }

    /**
     * 分页查询条件。
     *
     * @param memberId        会员侧查询时固定为当前会员（归属收口）；admin 侧为 null
     * @param filterMemberId  admin 按会员筛选；可空
     * @param orderNo         admin 按单号精确筛选；可空
     */
    record OrderPageQuery(Long memberId, Long filterMemberId, String orderNo, OrderStatus status,
                          Instant from, Instant to, int page, int size) {

        /** 会员侧分页。 */
        public static OrderPageQuery member(long memberId, OrderStatus status, Instant from, Instant to,
                                            int page, int size) {
            return new OrderPageQuery(memberId, null, null, status, from, to, page, size);
        }

        /** 管理侧分页。 */
        public static OrderPageQuery admin(String orderNo, Long filterMemberId, OrderStatus status, Instant from,
                                           Instant to, int page, int size) {
            return new OrderPageQuery(null, filterMemberId, orderNo, status, from, to, page, size);
        }
    }

    record OrderPage(List<Order> headers, long total, int page, int size) {
    }
}
