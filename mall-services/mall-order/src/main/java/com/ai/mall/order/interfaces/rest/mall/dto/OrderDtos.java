package com.ai.mall.order.interfaces.rest.mall.dto;

import com.ai.mall.common.web.annotation.StringId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 会员订单接口契约（CHG-0019，/api/mall/orders）。
 *
 * <p>雪花 ID 一律以 string 收发；金额为整数分；时间为 ISO-8601 字符串。
 * 前端任何金额字段都不被信任（服务端重算）。
 */
public final class OrderDtos {

    private OrderDtos() {
    }

    // ---------- 预览 ----------

    /** 预览/下单请求行（BUY_NOW 使用；CART 下单时服务端以令牌载荷为准，忽略此项）。 */
    public record OrderItemRequest(
            @NotBlank(message = "skuId 不能为空") String skuId,
            @NotNull(message = "quantity 不能为空")
            @Min(value = 1, message = "数量必须大于0")
            @Max(value = 999, message = "单品数量不能超过999件") Integer quantity) {
    }

    /** 订单预览请求。 */
    public record PreviewRequest(
            @NotBlank(message = "source 不能为空") String source,
            String addressId,
            @Valid List<OrderItemRequest> items) {
    }

    /** 正式创建订单请求（双层幂等：submitToken）。 */
    public record CreateOrderRequest(
            @NotBlank(message = "submitToken 不能为空") String submitToken,
            @NotBlank(message = "addressId 不能为空") String addressId,
            @NotBlank(message = "source 不能为空") String source,
            @Valid List<OrderItemRequest> items) {
    }

    /** 取消订单请求（原因可选）。 */
    public record CancelRequest(
            @Size(max = 255, message = "取消原因不能超过255字") String reason) {
    }

    /** 收货地址快照视图。 */
    public record ReceiverView(String receiverName, String receiverPhone, String province, String city,
                               String district, String detailAddress, String postalCode) {
    }

    /** 预览地址视图（比快照多 addressId，供确认页切换地址）。 */
    public record AddressView(String addressId, String receiverName, String receiverPhone, String province,
                              String city, String district, String detailAddress, String postalCode) {
    }

    /** 预览商品行。 */
    public record PreviewItemView(String skuId, String productId, String productName, String skuCode,
                                  Map<String, String> specifications, String mainImageUrl, int quantity,
                                  Long unitPriceFen, long subtotalFen, boolean salable,
                                  String stockStatus, List<String> issueCodes) {
    }

    /** 预览响应。 */
    public record PreviewView(String submitToken, AddressView address, List<PreviewItemView> items,
                              long goodsAmountFen, long discountAmountFen, long freightAmountFen,
                              long payAmountFen, boolean availableToSubmit) {
    }

    // ---------- 订单详情/列表 ----------

    /** 订单商品快照行。 */
    public record OrderItemView(String productId, String skuId, String productName, String skuCode,
                                Map<String, String> specifications, String mainImageUrl,
                                long unitPriceFen, int quantity, long subtotalFen) {
    }

    /** 状态历史轨迹行。 */
    public record StatusHistoryView(String fromStatus, String toStatus, String operation, String operator,
                                    String reason, Instant occurredAt) {
    }

    /** 订单完整视图（详情与写操作响应共用）。 */
    public record OrderView(@StringId long id, String orderNo, String status, String source,
                            long goodsAmountFen, long discountAmountFen, long freightAmountFen, long payAmountFen,
                            ReceiverView receiver, List<OrderItemView> items,
                            String deliveryCompany, String trackingNo, String cancelReason,
                            Instant createdAt, Instant paidAt, Instant cancelledAt, Instant shippedAt,
                            Instant completedAt, List<StatusHistoryView> statusHistory) {
    }

    /** 列表行的商品摘要。 */
    public record OrderSummaryItemView(String skuId, String productName, String mainImageUrl,
                                       int quantity, long unitPriceFen, long subtotalFen) {
    }

    /** 订单列表摘要行。 */
    public record OrderSummaryView(String orderNo, String status, String source,
                                   long goodsAmountFen, long payAmountFen, Instant createdAt,
                                   List<OrderSummaryItemView> items) {
    }

    /** 分页视图。 */
    public record PageView<T>(List<T> records, long total, int page, int size) {
    }
}
