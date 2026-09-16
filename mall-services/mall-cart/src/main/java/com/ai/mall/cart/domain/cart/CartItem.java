package com.ai.mall.cart.domain.cart;

/**
 * 购物车条目（写模型，CHG-0018 DU-BE-801）。
 *
 * <p>Redis Hash field=skuId，value=本 record 的 JSON。除选择状态与数量外，
 * 仅冗余加购时快照价 {@code priceFenAtAdded}（不信任前端价格；读模型不直接展示该价，
 * 实时价以 DU-BE-802 商品聚合为准）；不存商品名称/图片等易变信息。
 *
 * @param skuId           SKU ID（雪花 ID，应用层以 long 处理，边界序列化为字符串）
 * @param quantity        数量 1..999
 * @param selected        是否勾选（新条目默认 true，跨设备随 Hash 保持）
 * @param priceFenAtAdded 加购时刻的售价快照（整数分），来自 product 内部契约
 * @param createdAt       创建时间（ISO-8601 UTC 字符串）
 * @param updatedAt       最近更新时间（ISO-8601 UTC 字符串）
 */
public record CartItem(long skuId, int quantity, boolean selected, long priceFenAtAdded,
                       String createdAt, String updatedAt) {
}
