package com.ai.mall.order.interfaces.rest.mall;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.AuthenticatedSubject;
import com.ai.mall.common.security.SecurityContextFacade;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.CheckoutPreviewService;
import com.ai.mall.order.application.order.OrderCancelService;
import com.ai.mall.order.application.order.OrderCreateService;
import com.ai.mall.order.application.order.OrderQueryService;
import com.ai.mall.order.application.order.PaymentService;
import com.ai.mall.order.application.order.ReceiptService;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.ai.mall.order.domain.order.OrderItem;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderStatus;
import com.ai.mall.order.interfaces.rest.OrderViewAssembler;
import com.ai.mall.order.interfaces.rest.mall.dto.OrderDtos;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员订单接口（CHG-0019，/api/mall/orders）。
 *
 * <p>memberId 只取自安全上下文，任何入参均不得指定会员；越权与不存在统一 404。
 */
@RestController
@RequestMapping("/api/mall/orders")
public class MemberOrderController {

    private final CheckoutPreviewService checkoutPreviewService;
    private final OrderCreateService orderCreateService;
    private final PaymentService paymentService;
    private final OrderCancelService orderCancelService;
    private final OrderQueryService orderQueryService;
    private final ReceiptService receiptService;
    private final OrderViewAssembler assembler;

    public MemberOrderController(CheckoutPreviewService checkoutPreviewService,
                                 OrderCreateService orderCreateService, PaymentService paymentService,
                                 OrderCancelService orderCancelService, OrderQueryService orderQueryService,
                                 ReceiptService receiptService, OrderViewAssembler assembler) {
        this.checkoutPreviewService = checkoutPreviewService;
        this.orderCreateService = orderCreateService;
        this.paymentService = paymentService;
        this.orderCancelService = orderCancelService;
        this.orderQueryService = orderQueryService;
        this.receiptService = receiptService;
        this.assembler = assembler;
    }

    /** 结算预览：实时重查商品/库存/地址，可下单时签发 submitToken。 */
    @PostMapping("/preview")
    public UnifyResult<OrderDtos.PreviewView> preview(@Valid @RequestBody OrderDtos.PreviewRequest request) {
        long memberId = currentMemberId();
        Long addressId = parseId(request.addressId());
        List<CheckoutPreviewService.RequestLine> items = request.items() == null ? List.of()
                : request.items().stream()
                        .map(line -> new CheckoutPreviewService.RequestLine(Long.parseLong(line.skuId()),
                                line.quantity()))
                        .toList();
        var model = checkoutPreviewService.preview(memberId, request.source(), addressId, items);
        return UnifyResult.ok(assembler.toPreviewView(model));
    }

    /** 正式创建订单（双层幂等：submitToken）。 */
    @PostMapping
    public UnifyResult<OrderDtos.OrderView> create(@Valid @RequestBody OrderDtos.CreateOrderRequest request) {
        long memberId = currentMemberId();
        List<CheckoutPreviewService.RequestLine> items = request.items() == null ? List.of()
                : request.items().stream()
                        .map(line -> new CheckoutPreviewService.RequestLine(Long.parseLong(line.skuId()),
                                line.quantity()))
                        .toList();
        Order order = orderCreateService.create(memberId,
                new OrderCreateService.CreateOrderCommand(request.submitToken(), request.addressId(),
                        request.source(), items));
        return UnifyResult.ok(assembler.toView(order));
    }

    /** 会员订单分页（?status&page&size&startAt&endAt）。 */
    @GetMapping
    public UnifyResult<OrderDtos.PageView<OrderDtos.OrderSummaryView>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startAt,
            @RequestParam(required = false) String endAt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        long memberId = currentMemberId();
        OrderRepository.OrderPage orderPage = orderQueryService.memberPage(memberId, parseStatus(status),
                parseInstant(startAt), parseInstant(endAt), normalizePage(page), normalizeSize(size));
        List<Long> orderIds = orderPage.headers().stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemMap = orderQueryService.itemsOf(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::orderId));
        List<OrderDtos.OrderSummaryView> records = orderPage.headers().stream()
                .map(header -> assembler.toSummaryView(header, itemMap.getOrDefault(header.getId(), List.of())))
                .toList();
        return UnifyResult.ok(new OrderDtos.PageView<>(records, orderPage.total(), orderPage.page(),
                orderPage.size()));
    }

    /** 会员订单详情（越权 404）。 */
    @GetMapping("/{orderNo}")
    public UnifyResult<OrderDtos.OrderView> detail(@PathVariable String orderNo) {
        return UnifyResult.ok(assembler.toView(orderQueryService.memberDetail(currentMemberId(), orderNo)));
    }

    /** 模拟支付（重复支付幂等成功）。 */
    @PostMapping("/{orderNo}/pay")
    public UnifyResult<OrderDtos.OrderView> pay(@PathVariable String orderNo) {
        return UnifyResult.ok(assembler.toView(paymentService.pay(currentMemberId(), orderNo)));
    }

    /** 取消待支付订单（重复取消幂等成功）。 */
    @PostMapping("/{orderNo}/cancel")
    public UnifyResult<OrderDtos.OrderView> cancel(@PathVariable String orderNo,
                                                   @RequestBody(required = false) OrderDtos.CancelRequest request) {
        String reason = request == null ? null : request.reason();
        return UnifyResult.ok(assembler.toView(orderCancelService.cancel(currentMemberId(), orderNo, reason)));
    }

    /** 确认收货。 */
    @PostMapping("/{orderNo}/confirm-receipt")
    public UnifyResult<OrderDtos.OrderView> confirmReceipt(@PathVariable String orderNo) {
        return UnifyResult.ok(assembler.toView(receiptService.confirmReceipt(currentMemberId(), orderNo)));
    }

    // ---------- helpers ----------

    private static long currentMemberId() {
        AuthenticatedSubject subject = SecurityContextFacade.currentSubject()
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.UNAUTHORIZED,
                        "未登录"));
        return Long.parseLong(subject.subjectId());
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new BusinessException(OrderErrorCode.ADDRESS_NOT_OWNED, HttpStatus.BAD_REQUEST, "地址不合法");
        }
    }

    private static OrderStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(OrderErrorCode.ORDER_ITEMS_INVALID, HttpStatus.BAD_REQUEST, "订单状态筛选不合法");
        }
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(OrderErrorCode.ORDER_ITEMS_INVALID, HttpStatus.BAD_REQUEST, "时间参数格式不合法");
        }
    }

    private static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private static int normalizeSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, 100);
    }
}
