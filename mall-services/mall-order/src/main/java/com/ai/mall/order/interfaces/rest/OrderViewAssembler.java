package com.ai.mall.order.interfaces.rest;

import com.ai.mall.order.application.order.CheckoutPreviewService;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.OrderItem;
import com.ai.mall.order.domain.order.OrderStatusHistory;
import com.ai.mall.order.interfaces.rest.mall.dto.OrderDtos;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 订单领域模型 → REST 视图装配（CHG-0019）。会员端与管理端共用。
 */
@Component
public class OrderViewAssembler {

    /** 预览模型 → 预览响应。 */
    public OrderDtos.PreviewView toPreviewView(CheckoutPreviewService.PreviewModel model) {
        List<OrderDtos.PreviewItemView> items = model.items().stream()
                .map(line -> new OrderDtos.PreviewItemView(line.skuId(), line.productId(), line.productName(),
                        line.skuCode(), line.specifications(), line.mainImageUrl(), line.quantity(),
                        line.unitPriceFen(), line.subtotalFen(), line.salable(), line.stockStatus(),
                        line.issueCodes()))
                .toList();
        OrderDtos.AddressView address = model.address() == null ? null
                : new OrderDtos.AddressView(model.address().addressId(), model.address().receiverName(),
                        model.address().receiverPhone(), model.address().province(), model.address().city(),
                        model.address().district(), model.address().detailAddress(), model.address().postalCode());
        return new OrderDtos.PreviewView(model.submitToken(), address, items, model.goodsAmountFen(),
                model.discountAmountFen(), model.freightAmountFen(), model.payAmountFen(),
                model.availableToSubmit());
    }

    /** 完整聚合 → 订单详情视图。 */
    public OrderDtos.OrderView toView(Order order) {
        List<OrderDtos.OrderItemView> items = order.items().stream().map(this::toItemView).toList();
        List<OrderDtos.StatusHistoryView> histories = order.histories().stream().map(this::toHistoryView).toList();
        var receiver = new OrderDtos.ReceiverView(order.receiver().receiverName(), order.receiver().receiverPhone(),
                order.receiver().province(), order.receiver().city(), order.receiver().district(),
                order.receiver().detailAddress(), order.receiver().postalCode());
        return new OrderDtos.OrderView(order.getId(), order.orderNo(), order.status().name(), order.source().name(),
                order.money().goodsFen(), order.money().discountFen(), order.money().freightFen(),
                order.money().payFen(), receiver, items, order.deliveryCompany(), order.trackingNo(),
                order.cancelReason(), order.createdAt(), order.paidAt(), order.cancelledAt(), order.shippedAt(),
                order.completedAt(), histories);
    }

    /** 列表摘要行（商品行需由调用方按 orderId 批量装配后传入）。 */
    public OrderDtos.OrderSummaryView toSummaryView(Order order, List<OrderItem> items) {
        List<OrderDtos.OrderSummaryItemView> itemViews = items.stream()
                .map(item -> new OrderDtos.OrderSummaryItemView(Long.toString(item.skuId()), item.productName(),
                        item.mainImageUrl(), item.quantity(), item.unitPriceFen(), item.subtotalFen()))
                .toList();
        return new OrderDtos.OrderSummaryView(order.orderNo(), order.status().name(), order.source().name(),
                order.money().goodsFen(), order.money().payFen(), order.createdAt(), itemViews);
    }

    private OrderDtos.OrderItemView toItemView(OrderItem item) {
        return new OrderDtos.OrderItemView(Long.toString(item.productId()), Long.toString(item.skuId()),
                item.productName(), item.skuCode(), item.specifications(), item.mainImageUrl(),
                item.unitPriceFen(), item.quantity(), item.subtotalFen());
    }

    private OrderDtos.StatusHistoryView toHistoryView(OrderStatusHistory history) {
        return new OrderDtos.StatusHistoryView(
                history.fromStatus() == null ? null : history.fromStatus().name(),
                history.toStatus().name(), history.operation().name(), history.operator(), history.reason(),
                history.occurredAt());
    }
}
