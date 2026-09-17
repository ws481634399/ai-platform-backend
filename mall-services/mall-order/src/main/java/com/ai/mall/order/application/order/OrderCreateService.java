package com.ai.mall.order.application.order;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.port.CartSelectionPort;
import com.ai.mall.order.application.order.port.InventoryPort;
import com.ai.mall.order.application.order.port.MemberAddressPort;
import com.ai.mall.order.application.order.port.OrderCompensationPort;
import com.ai.mall.order.application.order.port.ProductSkuPort;
import com.ai.mall.order.application.order.port.SubmitTokenStore;
import com.ai.mall.order.domain.order.Money;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.ai.mall.order.domain.order.OrderItem;
import com.ai.mall.order.domain.order.OrderNoGenerator;
import com.ai.mall.order.domain.order.OrderRepository;
import com.ai.mall.order.domain.order.OrderSource;
import com.ai.mall.order.domain.order.ReceiverSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 订单创建应用服务（CHG-0019 REQ-M4-001）。
 *
 * <p>闭环要点：
 * <ul>
 *   <li>双层幂等：Redis 令牌 GETDEL 原子消费（第一道）+ DB {@code (member_id, submit_token)} 唯一键（第二道）；</li>
 *   <li>下单瞬间二次重查商品双状态/最新价/可售库存/地址归属，不信任前端任何字段；</li>
 *   <li>逐行锁库存（reservationId = orderNo:skuId，库存侧幂等）；中途失败回滚已锁行，
 *       回滚失败登记 RELEASE 补偿；</li>
 *   <li>锁成功但落库失败：释放全部预留并登记补偿；order_no 撞号换新号最多 3 次；</li>
 *   <li>CART 来源成功后尽力清理购物车已购行。</li>
 * </ul>
 */
@Service
public class OrderCreateService {

    private static final Logger log = LoggerFactory.getLogger(OrderCreateService.class);
    /** order_no 唯一键冲突时换新号重建次数上限。 */
    private static final int MAX_ORDER_NO_RETRY = 3;

    private final SubmitTokenStore submitTokenStore;
    private final OrderRepository orderRepository;
    private final ProductSkuPort productSkuPort;
    private final InventoryPort inventoryPort;
    private final MemberAddressPort memberAddressPort;
    private final CartSelectionPort cartSelectionPort;
    private final OrderCompensationPort compensationPort;
    private final OrderNoGenerator orderNoGenerator;

    public OrderCreateService(SubmitTokenStore submitTokenStore, OrderRepository orderRepository,
                              ProductSkuPort productSkuPort, InventoryPort inventoryPort,
                              MemberAddressPort memberAddressPort, CartSelectionPort cartSelectionPort,
                              OrderCompensationPort compensationPort, OrderNoGenerator orderNoGenerator) {
        this.submitTokenStore = submitTokenStore;
        this.orderRepository = orderRepository;
        this.productSkuPort = productSkuPort;
        this.inventoryPort = inventoryPort;
        this.memberAddressPort = memberAddressPort;
        this.cartSelectionPort = cartSelectionPort;
        this.compensationPort = compensationPort;
        this.orderNoGenerator = orderNoGenerator;
    }

    /** 创建订单命令（memberId 仅由安全上下文提供，Controller 不得透传）。 */
    public record CreateOrderCommand(String submitToken, String addressId, String source,
                                     List<CheckoutPreviewService.RequestLine> items) {
    }

    public Order create(long memberId, CreateOrderCommand command) {
        // ---- 第一道幂等：原子消费令牌 ----
        SubmitTokenStore.Payload payload = submitTokenStore.consume(memberId, command.submitToken())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.SUBMIT_TOKEN_INVALID, HttpStatus.BAD_REQUEST));

        OrderSource source;
        try {
            source = OrderSource.valueOf(command.source());
        } catch (RuntimeException ex) {
            throw new BusinessException(OrderErrorCode.SUBMIT_TOKEN_INVALID, HttpStatus.BAD_REQUEST, "订单来源不合法");
        }

        // ---- 令牌指纹校验（来源/地址/行必须与预览一致，改任何一项都要重新预览）----
        if (!source.name().equals(payload.source())
                || payload.addressId() == null
                || !payload.addressId().toString().equals(command.addressId())) {
            throw new BusinessException(OrderErrorCode.SUBMIT_TOKEN_INVALID, HttpStatus.BAD_REQUEST,
                    "下单内容与预览不一致，请重新确认订单");
        }
        List<CheckoutPreviewService.RequestLine> lines = normalizePayloadLines(payload);
        if (source == OrderSource.BUY_NOW && !fingerprintMatches(lines, command.items())) {
            throw new BusinessException(OrderErrorCode.SUBMIT_TOKEN_INVALID, HttpStatus.BAD_REQUEST,
                    "下单商品与预览不一致，请重新确认订单");
        }

        // ---- 第二道幂等：令牌已落过单 → 直接回放首单 ----
        Order replayed = orderRepository.findByMemberAndSubmitToken(memberId, command.submitToken()).orElse(null);
        if (replayed != null) {
            return replayed;
        }

        // ---- 下单瞬间二次重查：地址归属 ----
        MemberAddressPort.AddressSnapshot address =
                memberAddressPort.findOwnedAddress(memberId, payload.addressId())
                        .orElseThrow(() -> new BusinessException(OrderErrorCode.ADDRESS_NOT_OWNED,
                                HttpStatus.NOT_FOUND));

        // ---- 二次重查：商品双状态/最新价 + 可售库存 ----
        List<Long> skuIds = lines.stream().map(CheckoutPreviewService.RequestLine::skuId).toList();
        Map<Long, ProductSkuPort.SkuSnapshot> snapshotMap = new LinkedHashMap<>();
        for (ProductSkuPort.SkuSnapshot snapshot : productSkuPort.batchSnapshots(skuIds)) {
            if (snapshot.skuId() != null) {
                snapshotMap.put(snapshot.skuId(), snapshot);
            }
        }
        Map<Long, Long> availabilityMap = new LinkedHashMap<>();
        for (InventoryPort.Availability availability : inventoryPort.availability(skuIds)) {
            availabilityMap.put(availability.skuId(), availability.availableQty());
        }
        // 先完成二次校验并计算总额；OrderItem 携带 orderNo，必须在生成 orderNo 后构造（撞号重试时重建）
        long goodsAmount = 0L;
        for (CheckoutPreviewService.RequestLine line : lines) {
            ProductSkuPort.SkuSnapshot snapshot = snapshotMap.get(line.skuId());
            if (snapshot == null || !snapshot.salable() || snapshot.salePriceInCents() == null) {
                throw new BusinessException(OrderErrorCode.PRODUCT_NOT_SALABLE, HttpStatus.CONFLICT,
                        "商品已下架或不可售");
            }
            long available = availabilityMap.getOrDefault(line.skuId(), 0L);
            if (available < line.quantity()) {
                throw new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK, HttpStatus.CONFLICT, "库存不足");
            }
            goodsAmount += snapshot.salePriceInCents() * line.quantity();
        }

        ReceiverSnapshot receiver = new ReceiverSnapshot(address.receiverName(), address.receiverPhone(),
                address.province(), address.city(), address.district(), address.detailAddress(),
                address.postalCode());

        // ---- 锁库存 + 落库（order_no 撞号换新号重试）----
        DataAccessException lastDataFailure = null;
        for (int attempt = 1; attempt <= MAX_ORDER_NO_RETRY; attempt++) {
            String orderNo = orderNoGenerator.next();
            // orderNo 进入商品快照与预留业务键，每次撞号都按新号重建
            List<OrderItem> orderItems = buildItems(orderNo, lines, snapshotMap);
            List<OrderCompensationPort.InventoryLine> locked = lockAll(orderNo, orderItems);
            Order order = Order.create(orderNo, memberId, source, Money.ofM4(goodsAmount), receiver, orderItems,
                    command.submitToken(), java.time.Instant.now());
            try {
                orderRepository.insert(order);
            } catch (DuplicateKeyException duplicate) {
                // 先回滚本次预留
                releaseAfterFailure(orderNo, locked, "订单落库唯一键冲突回滚");
                // 令牌唯一键冲突：回放首单（并发竞态兜底）
                Order concurrent = orderRepository
                        .findByMemberAndSubmitToken(memberId, command.submitToken()).orElse(null);
                if (concurrent != null) {
                    return concurrent;
                }
                // 其余视为 order_no 撞号，换新号重试
                lastDataFailure = duplicate;
                continue;
            } catch (DataAccessException dataFailure) {
                releaseAfterFailure(orderNo, locked, "订单落库失败回滚");
                throw new BusinessException(OrderErrorCode.ORDER_CREATE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR,
                        "订单创建失败，请稍后重试");
            }
            // 成功：CART 来源事务提交后尽力清理购物车
            if (source == OrderSource.CART) {
                cartSelectionPort.deleteItems(memberId, skuIds);
            }
            return order;
        }
        log.error("订单创建重试耗尽仍失败 memberId={}, token={}", memberId, command.submitToken(), lastDataFailure);
        throw new BusinessException(OrderErrorCode.ORDER_CREATE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR,
                "订单创建失败，请稍后重试");
    }

    private List<OrderItem> buildItems(String orderNo, List<CheckoutPreviewService.RequestLine> lines,
                                       Map<Long, ProductSkuPort.SkuSnapshot> snapshotMap) {
        List<OrderItem> orderItems = new ArrayList<>(lines.size());
        for (CheckoutPreviewService.RequestLine line : lines) {
            ProductSkuPort.SkuSnapshot snapshot = snapshotMap.get(line.skuId());
            orderItems.add(new OrderItem(null, orderNo, snapshot.productId(), snapshot.skuId(),
                    snapshot.productName(), snapshot.skuCode(), snapshot.specifications(),
                    snapshot.mainImageUrl(), snapshot.salePriceInCents(), line.quantity()));
        }
        return orderItems;
    }

    private List<OrderCompensationPort.InventoryLine> lockAll(String orderNo, List<OrderItem> orderItems) {
        List<OrderCompensationPort.InventoryLine> locked = new ArrayList<>(orderItems.size());
        try {
            for (OrderItem item : orderItems) {
                String reservationId = orderNo + ":" + item.skuId();
                inventoryPort.lock(reservationId, item.skuId(), item.quantity());
                locked.add(new OrderCompensationPort.InventoryLine(item.skuId(), item.quantity(), reservationId));
            }
            return locked;
        } catch (RuntimeException lockFailure) {
            // 锁库存中途失败（不足/依赖故障）：立即回滚已锁行，回滚失败走补偿
            releaseAfterFailure(orderNo, locked, "锁库存中途失败回滚");
            throw lockFailure;
        }
    }

    /** 尽力逐行释放；失败行合并为一条补偿任务（release 幂等，重复安全）。 */
    private void releaseAfterFailure(String orderNo, List<OrderCompensationPort.InventoryLine> locked,
                                     String reason) {
        List<OrderCompensationPort.InventoryLine> failed = new ArrayList<>();
        for (OrderCompensationPort.InventoryLine line : locked) {
            try {
                inventoryPort.release(line.reservationId());
            } catch (RuntimeException releaseFailure) {
                log.warn("失败回滚释放预留失败 orderNo={}, reservationId={}", orderNo, line.reservationId(),
                        releaseFailure);
                failed.add(line);
            }
        }
        if (!failed.isEmpty()) {
            compensationPort.enqueueInventoryRelease(orderNo, failed, reason);
        }
    }

    private List<CheckoutPreviewService.RequestLine> normalizePayloadLines(SubmitTokenStore.Payload payload) {
        if (payload.items() == null || payload.items().isEmpty()) {
            throw new BusinessException(OrderErrorCode.SUBMIT_TOKEN_INVALID, HttpStatus.BAD_REQUEST, "令牌内容为空");
        }
        List<CheckoutPreviewService.RequestLine> lines = new ArrayList<>();
        for (SubmitTokenStore.Payload.Line line : payload.items()) {
            if (line.skuId() <= 0 || line.quantity() <= 0) {
                throw new BusinessException(OrderErrorCode.SUBMIT_TOKEN_INVALID, HttpStatus.BAD_REQUEST, "令牌内容非法");
            }
            lines.add(new CheckoutPreviewService.RequestLine(line.skuId(), line.quantity()));
        }
        return lines;
    }

    private boolean fingerprintMatches(List<CheckoutPreviewService.RequestLine> expected,
                                       List<CheckoutPreviewService.RequestLine> actual) {
        if (actual == null || actual.size() != expected.size()) {
            return false;
        }
        Map<Long, Integer> expectedMap = new LinkedHashMap<>();
        for (CheckoutPreviewService.RequestLine line : expected) {
            expectedMap.merge(line.skuId(), line.quantity(), Integer::sum);
        }
        Map<Long, Integer> actualMap = new LinkedHashMap<>();
        for (CheckoutPreviewService.RequestLine line : actual) {
            actualMap.merge(line.skuId(), line.quantity(), Integer::sum);
        }
        return expectedMap.equals(actualMap);
    }
}
