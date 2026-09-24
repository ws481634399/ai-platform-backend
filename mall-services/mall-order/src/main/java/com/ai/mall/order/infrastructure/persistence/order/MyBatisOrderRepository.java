package com.ai.mall.order.infrastructure.persistence.order;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery;

import com.ai.mall.order.application.order.event.OrderEventOutbox;
import com.ai.mall.order.domain.order.Money;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.OrderItem;
import com.ai.mall.order.domain.order.OrderOperation;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderSource;
import com.ai.mall.order.domain.order.OrderStatus;
import com.ai.mall.order.domain.order.OrderStatusHistory;
import com.ai.mall.order.domain.order.ReceiverSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单仓储 MyBatis-Plus 实现（CHG-0019）。
 *
 * <p>{@link #transition} 是状态迁移唯一落库路径：动作专属 CAS SQL 与 history 插入同事务；
 * 规格 JSON 以 TEXT 存储（H2 MODE=MySQL 与 MySQL 双兼容，禁用 JSON 专有类型）。
 */
@Repository
public class MyBatisOrderRepository implements OrderRepository {

    private static final TypeReference<Map<String, String>> SPEC_TYPE = new TypeReference<>() {
    };

    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderStatusHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;
    private final OrderEventOutbox eventOutbox;

    public MyBatisOrderRepository(OrderMapper orderMapper, OrderItemMapper itemMapper,
                                  OrderStatusHistoryMapper historyMapper, ObjectMapper objectMapper,
                                  OrderEventOutbox eventOutbox) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.historyMapper = historyMapper;
        this.objectMapper = objectMapper;
        this.eventOutbox = eventOutbox;
    }

    @Override
    @Transactional
    public void insert(Order order) {
        OrderPo po = toPo(order);
        orderMapper.insert(po);
        // 回填雪花主键给聚合（订单项/CREATE 历史均以该主表 id 落库）
        order.assignPersistedId(po.getId());

        for (OrderItem item : order.items()) {
            item.bindOrderId(po.getId());
            itemMapper.insert(toItemPo(item));
        }
        for (OrderStatusHistory history : order.histories()) {
            historyMapper.insert(toHistoryPo(po.getId(), history));
        }
        // 集成事件在同一事务内 flush（payload 需要已回填的 orderId，故置于最后）
        eventOutbox.flush(order);
    }

    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        OrderPo po = orderMapper.selectOne(lambdaQuery(OrderPo.class).eq(OrderPo::getOrderNo, orderNo));
        return Optional.ofNullable(po).map(this::toAggregate);
    }

    @Override
    public Optional<Order> findById(long orderId) {
        OrderPo po = orderMapper.selectById(orderId);
        return Optional.ofNullable(po).map(this::toAggregate);
    }

    @Override
    public Optional<OrderStatus> findStatusById(long orderId) {
        OrderPo po = orderMapper.selectById(orderId);
        return Optional.ofNullable(po).map(p -> OrderStatus.valueOf(p.getStatus()));
    }

    @Override
    public Optional<Order> findByMemberAndSubmitToken(long memberId, String submitToken) {
        OrderPo po = orderMapper.selectOne(lambdaQuery(OrderPo.class)
                .eq(OrderPo::getMemberId, memberId)
                .eq(OrderPo::getSubmitToken, submitToken));
        return Optional.ofNullable(po).map(this::toAggregate);
    }

    @Override
    @Transactional
    public boolean transition(StatusTransition change) {
        int rows = switch (change.operation()) {
            case PAY -> orderMapper.casPay(change.orderId(), change.expectedVersion(), change.occurredAt());
            case CANCEL -> orderMapper.casCancel(change.orderId(), change.expectedVersion(),
                    change.reason(), change.occurredAt());
            case SHIP -> orderMapper.casShip(change.orderId(), change.expectedVersion(),
                    change.deliveryCompany(), change.trackingNo(), change.occurredAt());
            case CONFIRM_RECEIPT -> orderMapper.casComplete(change.orderId(), change.expectedVersion(),
                    change.occurredAt());
            case CREATE -> throw new IllegalArgumentException("CREATE 不经 CAS 迁移");
        };
        if (rows == 1) {
            OrderStatusHistoryPo historyPo = new OrderStatusHistoryPo();
            historyPo.setOrderId(change.orderId());
            historyPo.setOrderNo(change.orderNo());
            historyPo.setFromStatus(change.from() == null ? null : change.from().name());
            historyPo.setToStatus(change.to().name());
            historyPo.setOperation(change.operation().name());
            historyPo.setOperator(change.operator());
            historyPo.setReason(change.reason());
            historyPo.setOccurredAt(change.occurredAt());
            historyMapper.insert(historyPo);
            // CAS 获胜方在同一事务内 flush 集成事件；落败不 flush
            eventOutbox.flush(change.order());
            return true;
        }
        return false;
    }

    @Override
    public OrderPage page(OrderPageQuery query) {
        var wrapper = lambdaQuery(OrderPo.class)
                .eq(query.memberId() != null, OrderPo::getMemberId, query.memberId())
                .eq(query.filterMemberId() != null, OrderPo::getMemberId, query.filterMemberId())
                .eq(query.orderNo() != null && !query.orderNo().isBlank(), OrderPo::getOrderNo, query.orderNo())
                .eq(query.status() != null, OrderPo::getStatus, query.status() == null ? null : query.status().name())
                .ge(query.from() != null, OrderPo::getCreatedAt, query.from())
                .le(query.to() != null, OrderPo::getCreatedAt, query.to())
                .orderByDesc(OrderPo::getCreatedAt);
        Page<OrderPo> poPage = orderMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        List<Order> headers = poPage.getRecords().stream().map(po -> toAggregate(po, List.of(), List.of())).toList();
        return new OrderPage(headers, poPage.getTotal(), query.page(), query.size());
    }

    @Override
    public List<OrderItem> findItemsByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return itemMapper.selectList(lambdaQuery(OrderItemPo.class)
                        .in(OrderItemPo::getOrderId, orderIds)
                        .orderByAsc(OrderItemPo::getId))
                .stream().map(this::toItem).toList();
    }

    // ---------- 聚合装配 ----------

    private Order toAggregate(OrderPo po) {
        List<OrderItemPo> itemPos = itemMapper.selectList(lambdaQuery(OrderItemPo.class)
                .eq(OrderItemPo::getOrderId, po.getId())
                .orderByAsc(OrderItemPo::getId));
        List<OrderStatusHistoryPo> historyPos = historyMapper.selectList(lambdaQuery(OrderStatusHistoryPo.class)
                .eq(OrderStatusHistoryPo::getOrderId, po.getId())
                .orderByAsc(OrderStatusHistoryPo::getOccurredAt)
                .orderByAsc(OrderStatusHistoryPo::getId));
        return toAggregate(po, itemPos.stream().map(this::toItem).toList(),
                historyPos.stream().map(this::toHistory).toList());
    }

    private Order toAggregate(OrderPo po, List<OrderItem> items, List<OrderStatusHistory> histories) {
        return Order.reconstitute(
                po.getId(), po.getOrderNo(), po.getMemberId(), OrderStatus.valueOf(po.getStatus()),
                OrderSource.valueOf(po.getSource()),
                Money.reconstitute(po.getGoodsAmount(), po.getDiscountAmount(), po.getFreightAmount(),
                        po.getPayAmount()),
                new ReceiverSnapshot(po.getReceiverName(), po.getReceiverPhone(), po.getReceiverProvince(),
                        po.getReceiverCity(), po.getReceiverDistrict(), po.getReceiverDetailAddress(),
                        po.getReceiverPostalCode()),
                items, po.getDeliveryCompany(), po.getTrackingNo(), po.getCancelReason(), po.getSubmitToken(),
                po.getPaidAt(), po.getCancelledAt(), po.getShippedAt(), po.getCompletedAt(),
                po.getVersion() == null ? 0L : po.getVersion(), po.getCreatedAt(), po.getUpdatedAt(), histories);
    }

    // ---------- PO 映射 ----------

    private OrderPo toPo(Order order) {
        OrderPo po = new OrderPo();
        po.setOrderNo(order.orderNo());
        po.setMemberId(order.memberId());
        po.setStatus(order.status().name());
        po.setSource(order.source().name());
        po.setGoodsAmount(order.money().goodsFen());
        po.setDiscountAmount(order.money().discountFen());
        po.setFreightAmount(order.money().freightFen());
        po.setPayAmount(order.money().payFen());
        po.setReceiverName(order.receiver().receiverName());
        po.setReceiverPhone(order.receiver().receiverPhone());
        po.setReceiverProvince(order.receiver().province());
        po.setReceiverCity(order.receiver().city());
        po.setReceiverDistrict(order.receiver().district());
        po.setReceiverDetailAddress(order.receiver().detailAddress());
        po.setReceiverPostalCode(order.receiver().postalCode());
        po.setSubmitToken(order.submitToken());
        po.setVersion(0L);
        Instant now = order.createdAt();
        po.setCreatedAt(now);
        po.setUpdatedAt(now);
        return po;
    }

    private OrderItemPo toItemPo(OrderItem item) {
        OrderItemPo po = new OrderItemPo();
        po.setOrderId(item.orderId());
        po.setOrderNo(item.orderNo());
        po.setProductId(item.productId());
        po.setSkuId(item.skuId());
        po.setProductName(item.productName());
        po.setSkuCode(item.skuCode());
        po.setSpecificationsJson(writeSpecs(item.specifications()));
        po.setMainImageUrl(item.mainImageUrl());
        po.setUnitPriceFen(item.unitPriceFen());
        po.setQuantity(item.quantity());
        po.setSubtotalFen(item.subtotalFen());
        po.setCreatedAt(Instant.now());
        return po;
    }

    private OrderItem toItem(OrderItemPo po) {
        return OrderItem.reconstitute(po.getId(), po.getOrderId(), po.getOrderNo(), po.getProductId(),
                po.getSkuId(), po.getProductName(), po.getSkuCode(), readSpecs(po.getSpecificationsJson()),
                po.getMainImageUrl(), po.getUnitPriceFen(), po.getQuantity(), po.getSubtotalFen());
    }

    private OrderStatusHistoryPo toHistoryPo(long orderId, OrderStatusHistory history) {
        OrderStatusHistoryPo po = new OrderStatusHistoryPo();
        po.setOrderId(orderId);
        po.setOrderNo(history.orderNo());
        po.setFromStatus(history.fromStatus() == null ? null : history.fromStatus().name());
        po.setToStatus(history.toStatus().name());
        po.setOperation(history.operation().name());
        po.setOperator(history.operator());
        po.setReason(history.reason());
        po.setOccurredAt(history.occurredAt());
        return po;
    }

    private OrderStatusHistory toHistory(OrderStatusHistoryPo po) {
        return OrderStatusHistory.reconstitute(po.getId(), po.getOrderId(), po.getOrderNo(),
                po.getFromStatus() == null ? null : OrderStatus.valueOf(po.getFromStatus()),
                OrderStatus.valueOf(po.getToStatus()), OrderOperation.valueOf(po.getOperation()),
                po.getOperator(), po.getReason(), po.getOccurredAt());
    }

    private String writeSpecs(Map<String, String> specs) {
        if (specs == null || specs.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(specs);
        } catch (Exception ex) {
            throw new IllegalArgumentException("规格序列化失败", ex);
        }
    }

    private Map<String, String> readSpecs(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, SPEC_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("规格反序列化失败", ex);
        }
    }
}
