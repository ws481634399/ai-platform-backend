package com.ai.mall.order.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.OrderQueryService;
import com.ai.mall.order.application.order.ShipmentService;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.ai.mall.order.domain.order.OrderItem;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderStatus;
import com.ai.mall.order.interfaces.rest.OrderViewAssembler;
import com.ai.mall.order.interfaces.rest.admin.dto.AdminOrderDtos;
import com.ai.mall.order.interfaces.rest.mall.dto.OrderDtos;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端订单接口（CHG-0019 REQ-M4-003，/api/admin/orders）。
 *
 * <p>路径层 ROLE_ADMIN 收口，方法层权限码 order:list / order:view / order:ship 细粒度控制。
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderQueryService orderQueryService;
    private final ShipmentService shipmentService;
    private final OrderViewAssembler assembler;

    public AdminOrderController(OrderQueryService orderQueryService, ShipmentService shipmentService,
                                OrderViewAssembler assembler) {
        this.orderQueryService = orderQueryService;
        this.shipmentService = shipmentService;
        this.assembler = assembler;
    }

    /** 订单分页（支持单号/会员/状态/时间筛选）。 */
    @GetMapping
    @PreAuthorize("hasAuthority('order:list')")
    public UnifyResult<OrderDtos.PageView<OrderDtos.OrderSummaryView>> page(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startAt,
            @RequestParam(required = false) String endAt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        OrderRepository.OrderPage orderPage = orderQueryService.adminPage(
                blankToNull(orderNo), parseMemberId(memberId), parseStatus(status),
                parseInstant(startAt), parseInstant(endAt), Math.max(page, 1), clampSize(size));
        List<Long> orderIds = orderPage.headers().stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemMap = orderQueryService.itemsOf(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::orderId));
        List<OrderDtos.OrderSummaryView> records = orderPage.headers().stream()
                .map(header -> assembler.toSummaryView(header, itemMap.getOrDefault(header.getId(), List.of())))
                .toList();
        return UnifyResult.ok(new OrderDtos.PageView<>(records, orderPage.total(), orderPage.page(),
                orderPage.size()));
    }

    /** 订单详情。 */
    @GetMapping("/{orderNo}")
    @PreAuthorize("hasAuthority('order:view')")
    public UnifyResult<OrderDtos.OrderView> detail(@PathVariable String orderNo) {
        return UnifyResult.ok(assembler.toView(orderQueryService.adminDetail(orderNo)));
    }

    /** 发货（PAID→SHIPPED，记录物流信息）。 */
    @PostMapping("/{orderNo}/ship")
    @PreAuthorize("hasAuthority('order:ship')")
    public UnifyResult<OrderDtos.OrderView> ship(@PathVariable String orderNo,
                                                 @Valid @RequestBody AdminOrderDtos.ShipRequest request) {
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        return UnifyResult.ok(assembler.toView(
                shipmentService.ship(orderNo, request.deliveryCompany(), request.trackingNo(), operator)));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Long parseMemberId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(OrderErrorCode.ORDER_ITEMS_INVALID, HttpStatus.BAD_REQUEST, "会员筛选不合法");
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

    private static int clampSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, 100);
    }
}
