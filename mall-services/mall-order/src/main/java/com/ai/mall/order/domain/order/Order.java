package com.ai.mall.order.domain.order;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.domain.order.event.OrderIntegrationEvent;
import com.ai.mall.order.domain.order.event.OrderIntegrationEventType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * 订单聚合根（CHG-0019）。
 *
 * <p>订单状态机的唯一入口：任何状态迁移只能经 {@link #pay}/{@link #cancel}/{@link #ship}/
 * {@link #confirmReceipt} 发生，Service/Controller/Mapper 不得自行 setStatus。
 * 持久层以 CAS（status+version 条件更新）仲裁并发，仅 CAS 获胜方的迁移生效并触发库存副作用。
 * 金额、地址、商品行均为不可变快照。
 */
public class Order {

    private Long id;
    private final String orderNo;
    private final long memberId;
    private OrderStatus status;
    private final OrderSource source;
    private final Money money;
    private final ReceiverSnapshot receiver;
    private final List<OrderItem> items;
    private final List<OrderStatusHistory> histories;
    /** 本次业务周期内收集的待发集成事件（仅内存，持久层重建时为空）。 */
    private final List<OrderIntegrationEvent> integrationEvents;

    private String deliveryCompany;
    private String trackingNo;
    private String cancelReason;
    private final String submitToken;

    private Instant paidAt;
    private Instant cancelledAt;
    private Instant shippedAt;
    private Instant completedAt;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Order(Long id, String orderNo, long memberId, OrderStatus status, OrderSource source, Money money,
                  ReceiverSnapshot receiver, List<OrderItem> items, String deliveryCompany, String trackingNo,
                  String cancelReason, String submitToken, Instant paidAt, Instant cancelledAt, Instant shippedAt,
                  Instant completedAt, long version, Instant createdAt, Instant updatedAt,
                  List<OrderStatusHistory> histories, List<OrderIntegrationEvent> integrationEvents) {
        this.id = id;
        this.orderNo = orderNo;
        this.memberId = memberId;
        this.status = status;
        this.source = source;
        this.money = money;
        this.receiver = receiver;
        this.items = items;
        this.deliveryCompany = deliveryCompany;
        this.trackingNo = trackingNo;
        this.cancelReason = cancelReason;
        this.submitToken = submitToken;
        this.paidAt = paidAt;
        this.cancelledAt = cancelledAt;
        this.shippedAt = shippedAt;
        this.completedAt = completedAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.histories = histories;
        this.integrationEvents = integrationEvents;
    }

    /**
     * 创建待支付订单（状态机起点）。
     * 聚合创建即追加 CREATE 历史；订单主键由仓储落库回填。
     */
    public static Order create(String orderNo, long memberId, OrderSource source, Money money,
                               ReceiverSnapshot receiver, List<OrderItem> items, String submitToken, Instant now) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("orderNo 不能为空");
        }
        if (memberId <= 0) {
            throw new IllegalArgumentException("memberId 必须为正");
        }
        if (money == null || receiver == null) {
            throw new IllegalArgumentException("订单金额/收货快照不能为空");
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException(OrderErrorCode.ORDER_ITEMS_INVALID, HttpStatus.BAD_REQUEST);
        }
        List<OrderItem> lines = new ArrayList<>(items);
        List<OrderStatusHistory> histories = new ArrayList<>();
        List<OrderIntegrationEvent> integrationEvents = new ArrayList<>();
        Order order = new Order(null, orderNo, memberId, OrderStatus.PENDING_PAYMENT, source, money, receiver,
                lines, null, null, null, submitToken, null, null, null, null, 0L, now, now,
                histories, integrationEvents);
        order.histories.add(new OrderStatusHistory(0L, orderNo, null, OrderStatus.PENDING_PAYMENT,
                OrderOperation.CREATE, Long.toString(memberId), null, now));
        order.integrationEvents.add(new OrderIntegrationEvent(OrderIntegrationEventType.ORDER_CREATED));
        // 延迟取消检查：与 ORDER_CREATED 同事务写 Outbox，投递时路由 order-delay Topic
        order.integrationEvents.add(new OrderIntegrationEvent(OrderIntegrationEventType.PAYMENT_TIMEOUT_CHECK));
        return order;
    }

    /** 持久化完整重建（含商品行与状态历史）。 */
    public static Order reconstitute(Long id, String orderNo, long memberId, OrderStatus status, OrderSource source,
                                     Money money, ReceiverSnapshot receiver, List<OrderItem> items,
                                     String deliveryCompany, String trackingNo, String cancelReason,
                                     String submitToken, Instant paidAt, Instant cancelledAt, Instant shippedAt,
                                     Instant completedAt, long version, Instant createdAt, Instant updatedAt,
                                     List<OrderStatusHistory> histories) {
        return new Order(id, orderNo, memberId, status, source, money, receiver,
                items == null ? new ArrayList<>() : new ArrayList<>(items),
                deliveryCompany, trackingNo, cancelReason, submitToken,
                paidAt, cancelledAt, shippedAt, completedAt, version, createdAt, updatedAt,
                histories == null ? new ArrayList<>() : new ArrayList<>(histories),
                // 持久层重建不携带待发事件，事件只在业务迁移的内存周期内存在
                new ArrayList<>());
    }

    /** 当前状态对某操作的迁移判定（首次/幂等重复/非法）。 */
    public OrderStatus.TransitionOutcome outcome(OrderOperation operation) {
        return status.evaluate(operation);
    }

    /** 支付：仅 PENDING_PAYMENT 可迁移，返回新增历史。 */
    public OrderStatusHistory pay(String operator, Instant now) {
        requireMutable(OrderOperation.PAY);
        this.status = OrderStatus.PAID;
        this.paidAt = now;
        this.updatedAt = now;
        integrationEvents.add(new OrderIntegrationEvent(OrderIntegrationEventType.PAYMENT_SUCCEEDED));
        return appendHistory(OrderOperation.PAY, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, operator, null, now);
    }

    /** 会员取消：仅 PENDING_PAYMENT 可迁移，记录取消原因。 */
    public OrderStatusHistory cancel(String operator, String reason, Instant now) {
        requireMutable(OrderOperation.CANCEL);
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = now;
        this.updatedAt = now;
        integrationEvents.add(new OrderIntegrationEvent(OrderIntegrationEventType.ORDER_CANCELLED));
        return appendHistory(OrderOperation.CANCEL, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED,
                operator, reason, now);
    }

    /** 后台发货：仅 PAID 可迁移，落物流公司与运单号。 */
    public OrderStatusHistory ship(String operator, String deliveryCompany, String trackingNo, Instant now) {
        requireMutable(OrderOperation.SHIP);
        if (deliveryCompany == null || deliveryCompany.isBlank() || trackingNo == null || trackingNo.isBlank()) {
            throw new IllegalArgumentException("物流公司与运单号不能为空");
        }
        this.status = OrderStatus.SHIPPED;
        this.deliveryCompany = deliveryCompany.trim();
        this.trackingNo = trackingNo.trim();
        this.shippedAt = now;
        this.updatedAt = now;
        return appendHistory(OrderOperation.SHIP, OrderStatus.PAID, OrderStatus.SHIPPED, operator, null, now);
    }

    /** 会员确认收货：仅 SHIPPED 可迁移。 */
    public OrderStatusHistory confirmReceipt(String operator, Instant now) {
        requireMutable(OrderOperation.CONFIRM_RECEIPT);
        this.status = OrderStatus.COMPLETED;
        this.completedAt = now;
        this.updatedAt = now;
        integrationEvents.add(new OrderIntegrationEvent(OrderIntegrationEventType.ORDER_COMPLETED));
        return appendHistory(OrderOperation.CONFIRM_RECEIPT, OrderStatus.SHIPPED, OrderStatus.COMPLETED,
                operator, null, now);
    }

    private void requireMutable(OrderOperation operation) {
        if (status.evaluate(operation) != OrderStatus.TransitionOutcome.MUTATED) {
            throw new BusinessException(OrderErrorCode.STATUS_CONFLICT, HttpStatus.CONFLICT);
        }
    }

    private OrderStatusHistory appendHistory(OrderOperation operation, OrderStatus from, OrderStatus to,
                                             String operator, String reason, Instant now) {
        OrderStatusHistory history = new OrderStatusHistory(
                id == null ? 0L : id, orderNo, from, to, operation, operator, reason, now);
        this.histories.add(history);
        return history;
    }

    /**
     * 取出并清空待发集成事件（flush 后调用）。
     * 调用方得到可变列表副本；聚合内列表同步清空，防止重复 flush。
     */
    public List<OrderIntegrationEvent> pullIntegrationEvents() {
        List<OrderIntegrationEvent> pulled = new ArrayList<>(integrationEvents);
        integrationEvents.clear();
        return pulled;
    }

    /** 仓储落库回填订单雪花主键（CREATE 历史以主表 id 插入，由仓储映射时统一取值）。 */
    public void assignPersistedId(long id) {
        this.id = id;
    }

    public Long getId() { return id; }
    public String orderNo() { return orderNo; }
    public long memberId() { return memberId; }
    public OrderStatus status() { return status; }
    public OrderSource source() { return source; }
    public Money money() { return money; }
    public ReceiverSnapshot receiver() { return receiver; }
    public List<OrderItem> items() { return items; }
    public List<OrderStatusHistory> histories() { return histories; }
    public String deliveryCompany() { return deliveryCompany; }
    public String trackingNo() { return trackingNo; }
    public String cancelReason() { return cancelReason; }
    public String submitToken() { return submitToken; }
    public Instant paidAt() { return paidAt; }
    public Instant cancelledAt() { return cancelledAt; }
    public Instant shippedAt() { return shippedAt; }
    public Instant completedAt() { return completedAt; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    /** 该订单每个商品行对应的库存预留业务键（reservationId = orderNo:skuId）。 */
    public List<String> reservationIds() {
        return items.stream().map(item -> item.orderNo() + ":" + item.skuId()).toList();
    }
}
