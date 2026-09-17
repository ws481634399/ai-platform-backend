package com.ai.mall.order.application.order.port;

import java.util.List;

/**
 * mall-cart 出站端口：读取会员当前勾选的购物车商品（CHG-0019）。
 *
 * <p>仅 CART 来源预览使用；返回的 skuId/数量作为令牌载荷指纹，
 * 正式下单以令牌载荷为准（忽略请求体 items）。传输故障由实现归一为 503。
 */
public interface CartSelectionPort {

    List<SelectedItem> selectedItems(long memberId);

    /**
     * CART 来源下单成功后，按 skuId 删除会员购物车中对应行（尽力而为）。
     * 传输故障由调用方记录 WARN，不影响已创建订单（用户可手动清理）。
     */
    void deleteItems(long memberId, List<Long> skuIds);

    record SelectedItem(long skuId, int quantity) {
    }
}
