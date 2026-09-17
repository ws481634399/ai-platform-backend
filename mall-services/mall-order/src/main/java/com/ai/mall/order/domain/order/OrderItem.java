package com.ai.mall.order.domain.order;

import java.util.Map;

/**
 * 订单项（商品快照实体，CHG-0019）。
 *
 * <p>价格、名称、规格、图片均为创建订单瞬间的快照；后续商品改名/调价/下架不影响历史订单。
 * 金额整数分，{@code subtotalFen = unitPriceFen * quantity}，由服务端重算后构造。
 */
public class OrderItem {

    /** 持久化回填（新建未落库时为 null）。 */
    private Long id;
    /** 订单主表雪花主键，orders 插入成功后回填；重建时直接给定。 */
    private Long orderId;
    private final String orderNo;
    private final long productId;
    private final long skuId;
    private final String productName;
    private final String skuCode;
    private final Map<String, String> specifications;
    private final String mainImageUrl;
    private final long unitPriceFen;
    private final int quantity;
    private final long subtotalFen;

    public OrderItem(Long orderId, String orderNo, long productId, long skuId, String productName, String skuCode,
                     Map<String, String> specifications, String mainImageUrl, long unitPriceFen, int quantity) {
        if (productId <= 0 || skuId <= 0) {
            throw new IllegalArgumentException("订单项 productId/skuId 必须为正");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("订单项商品名称不能为空");
        }
        if (unitPriceFen < 0 || quantity <= 0) {
            throw new IllegalArgumentException("订单项单价/数量非法");
        }
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.productId = productId;
        this.skuId = skuId;
        this.productName = productName;
        this.skuCode = skuCode;
        this.specifications = specifications == null ? Map.of() : Map.copyOf(specifications);
        this.mainImageUrl = mainImageUrl;
        this.unitPriceFen = unitPriceFen;
        this.quantity = quantity;
        this.subtotalFen = unitPriceFen * quantity;
    }

    /** 持久化重建（subtotal 以库内为准，仍校验勾稽关系）。 */
    public static OrderItem reconstitute(Long id, Long orderId, String orderNo, long productId, long skuId,
                                         String productName, String skuCode, Map<String, String> specifications,
                                         String mainImageUrl, long unitPriceFen, int quantity, long subtotalFen) {
        OrderItem item = new OrderItem(orderId, orderNo, productId, skuId, productName, skuCode,
                specifications, mainImageUrl, unitPriceFen, quantity);
        if (item.subtotalFen != subtotalFen) {
            throw new IllegalArgumentException("订单项小计与单价*数量不一致");
        }
        item.id = id;
        return item;
    }

    /** 订单主表落库后回填订单主键（订单项插入前，由仓储调用一次）。 */
    public void bindOrderId(long orderId) {
        if (this.orderId != null && this.orderId != orderId) {
            throw new IllegalStateException("订单项已绑定其他订单");
        }
        this.orderId = orderId;
    }

    void assignPersistedId(long id) {
        this.id = id;
    }

    public Long getId() { return id; }
    public Long orderId() { return orderId; }
    public String orderNo() { return orderNo; }
    public long productId() { return productId; }
    public long skuId() { return skuId; }
    public String productName() { return productName; }
    public String skuCode() { return skuCode; }
    public Map<String, String> specifications() { return specifications; }
    public String mainImageUrl() { return mainImageUrl; }
    public long unitPriceFen() { return unitPriceFen; }
    public int quantity() { return quantity; }
    public long subtotalFen() { return subtotalFen; }
}
