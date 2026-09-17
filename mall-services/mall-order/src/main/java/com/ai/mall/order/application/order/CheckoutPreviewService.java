package com.ai.mall.order.application.order;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.port.CartSelectionPort;
import com.ai.mall.order.application.order.port.InventoryPort;
import com.ai.mall.order.application.order.port.MemberAddressPort;
import com.ai.mall.order.application.order.port.ProductSkuPort;
import com.ai.mall.order.application.order.port.SubmitTokenStore;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.ai.mall.order.domain.order.OrderSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 订单预览应用服务（CHG-0019 REQ-M4-001）。
 *
 * <p>预览不产生订单、不锁库存；商品双状态/最新价/可售库存/地址归属全部服务端实时重查，
 * 不信任任何前端价格。可下单时签发一次性 submitToken（载荷绑定来源/地址/行指纹），
 * 换地址、改行、改数量必须重新预览。
 */
@Service
public class CheckoutPreviewService {

    /** 单次结算最多 100 种不同 SKU。 */
    public static final int MAX_DISTINCT_SKU = 100;
    /** 单行数量上限 1..999。 */
    public static final int MAX_QUANTITY = 999;
    /** 低库存阈值（仅展示提示，不影响可下单判定）。 */
    private static final long LOW_STOCK_THRESHOLD = 10L;

    private final ProductSkuPort productSkuPort;
    private final InventoryPort inventoryPort;
    private final MemberAddressPort memberAddressPort;
    private final CartSelectionPort cartSelectionPort;
    private final SubmitTokenStore submitTokenStore;
    private final long tokenTtlSeconds;

    public CheckoutPreviewService(ProductSkuPort productSkuPort, InventoryPort inventoryPort,
                                  MemberAddressPort memberAddressPort, CartSelectionPort cartSelectionPort,
                                  SubmitTokenStore submitTokenStore,
                                  @Value("${mall.order.submit-token-ttl-seconds:600}") long tokenTtlSeconds) {
        this.productSkuPort = productSkuPort;
        this.inventoryPort = inventoryPort;
        this.memberAddressPort = memberAddressPort;
        this.cartSelectionPort = cartSelectionPort;
        this.submitTokenStore = submitTokenStore;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    /**
     * 生成预览。
     *
     * @param memberId  当前会员（仅取自安全上下文）
     * @param source    CART / BUY_NOW
     * @param addressId 选中的地址 id（可空：未选地址时不可下单）
     * @param requestItems BUY_NOW 的请求行；CART 来源忽略（以购物车勾选项为准）
     */
    public PreviewModel preview(long memberId, String source, Long addressId, List<RequestLine> requestItems) {
        OrderSource orderSource = parseSource(source);
        List<RequestLine> requestedLines = resolveLines(memberId, orderSource, requestItems);

        // 地址实时校验（归属双条件在 mall-member 内部端点收口）
        PreviewAddress address = null;
        if (addressId != null) {
            address = memberAddressPort.findOwnedAddress(memberId, addressId)
                    .map(a -> new PreviewAddress(Long.toString(a.addressId()), a.receiverName(), a.receiverPhone(),
                            a.province(), a.city(), a.district(), a.detailAddress(), a.postalCode()))
                    .orElse(null);
        }

        if (requestedLines.isEmpty()) {
            // 空车/无选购项：返回空预览（不可下单，不签发令牌），前端引导去选购
            return new PreviewModel(null, null, List.of(), 0L, 0L, 0L, 0L, false);
        }

        // 商品快照 + 可售库存实时重查
        List<Long> skuIds = requestedLines.stream().map(RequestLine::skuId).toList();
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

        List<PreviewLine> lines = new ArrayList<>(requestedLines.size());
        boolean allSubmittable = address != null;
        long goodsAmount = 0L;
        for (RequestLine line : requestedLines) {
            ProductSkuPort.SkuSnapshot snapshot = snapshotMap.get(line.skuId());
            List<String> issueCodes = new ArrayList<>();
            boolean salable = snapshot != null && snapshot.salable() && snapshot.salePriceInCents() != null;
            long unitPrice = salable ? snapshot.salePriceInCents() : 0L;
            long subtotal = 0L;
            String stockStatus = "OUT_OF_STOCK";
            if (!salable) {
                issueCodes.add("SKU_NOT_SALABLE");
            } else {
                subtotal = unitPrice * line.quantity();
                goodsAmount += subtotal;
                long available = availabilityMap.getOrDefault(line.skuId(), 0L);
                if (available <= 0) {
                    stockStatus = "OUT_OF_STOCK";
                    issueCodes.add("OUT_OF_STOCK");
                } else if (available < line.quantity()) {
                    stockStatus = "LOW";
                    issueCodes.add("STOCK_INSUFFICIENT");
                } else {
                    stockStatus = available <= LOW_STOCK_THRESHOLD ? "LOW" : "OK";
                }
            }
            if (!issueCodes.isEmpty()) {
                allSubmittable = false;
            }
            lines.add(new PreviewLine(
                    Long.toString(line.skuId()),
                    snapshot != null && snapshot.productId() != null ? Long.toString(snapshot.productId()) : null,
                    salable ? snapshot.productName() : null,
                    salable ? snapshot.skuCode() : null,
                    salable ? snapshot.specifications() : Map.of(),
                    salable ? snapshot.mainImageUrl() : null,
                    line.quantity(),
                    salable ? unitPrice : null,
                    salable ? subtotal : 0L,
                    salable, stockStatus, issueCodes));
        }

        String submitToken = null;
        if (allSubmittable) {
            submitToken = UUID.randomUUID().toString().replace("-", "");
            List<SubmitTokenStore.Payload.Line> payloadLines = requestedLines.stream()
                    .map(line -> new SubmitTokenStore.Payload.Line(line.skuId(), line.quantity()))
                    .toList();
            submitTokenStore.issue(memberId, submitToken,
                    new SubmitTokenStore.Payload(orderSource.name(), addressId, payloadLines),
                    Duration.ofSeconds(tokenTtlSeconds));
        }

        return new PreviewModel(submitToken, address, lines, goodsAmount, 0L, 0L, goodsAmount, allSubmittable);
    }

    private List<RequestLine> resolveLines(long memberId, OrderSource source, List<RequestLine> requestItems) {
        List<RequestLine> raw = source == OrderSource.CART
                ? cartSelectionPort.selectedItems(memberId).stream()
                        .map(item -> new RequestLine(item.skuId(), item.quantity())).toList()
                : normalizeRequestItems(requestItems);
        // 合并同 SKU 行（CART 勾选不会重复，BUY_NOW 防御性合并），数量封顶 999
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (RequestLine line : raw) {
            int summed = merged.getOrDefault(line.skuId(), 0) + line.quantity();
            merged.put(line.skuId(), Math.min(summed, MAX_QUANTITY));
        }
        if (merged.size() > MAX_DISTINCT_SKU) {
            throw new BusinessException(OrderErrorCode.ORDER_ITEMS_INVALID, HttpStatus.BAD_REQUEST,
                    "单次结算最多 " + MAX_DISTINCT_SKU + " 种商品");
        }
        return merged.entrySet().stream()
                .map(entry -> new RequestLine(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<RequestLine> normalizeRequestItems(List<RequestLine> requestItems) {
        if (requestItems == null || requestItems.isEmpty()) {
            throw new BusinessException(OrderErrorCode.ORDER_ITEMS_INVALID, HttpStatus.BAD_REQUEST, "购买商品不能为空");
        }
        for (RequestLine line : requestItems) {
            if (line == null || line.skuId() <= 0 || line.quantity() < 1 || line.quantity() > MAX_QUANTITY) {
                throw new BusinessException(OrderErrorCode.SKU_INVALID, HttpStatus.BAD_REQUEST, "商品或数量不合法");
            }
        }
        return requestItems;
    }

    private static OrderSource parseSource(String source) {
        try {
            return OrderSource.valueOf(source);
        } catch (RuntimeException ex) {
            throw new BusinessException(OrderErrorCode.ORDER_ITEMS_INVALID, HttpStatus.BAD_REQUEST, "订单来源不合法");
        }
    }

    /** 入参归一后的请求行。 */
    public record RequestLine(long skuId, int quantity) {
    }

    /** 预览中的地址视图。 */
    public record PreviewAddress(String addressId, String receiverName, String receiverPhone,
                                 String province, String city, String district, String detailAddress,
                                 String postalCode) {
    }

    /** 预览商品行（金额整数分；不可售时 unitPriceFen 为 null）。 */
    public record PreviewLine(String skuId, String productId, String productName, String skuCode,
                              Map<String, String> specifications, String mainImageUrl, int quantity,
                              Long unitPriceFen, long subtotalFen, boolean salable,
                              String stockStatus, List<String> issueCodes) {
    }

    /** 预览聚合结果（M4 discount/freight 恒 0）。 */
    public record PreviewModel(String submitToken, PreviewAddress address, List<PreviewLine> items,
                               long goodsAmountFen, long discountAmountFen, long freightAmountFen,
                               long payAmountFen, boolean availableToSubmit) {
    }
}
