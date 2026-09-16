package com.ai.mall.cart.application.cart;

import java.util.List;
import java.util.Map;

/**
 * 购物车读模型（CHG-0018 DU-BE-802）。
 *
 * <p>GET /api/mall/cart 的应用层视图：车条目经 product 快照 + inventory 精确库存实时聚合，
 * 双状态（itemStatus / stockStatus）+ 调价检测 + 条目级降级；整车恒可装配（依赖故障也 200）。
 * 本模型只读，装配过程绝不改写 Redis（AC-008~013）。
 */
public final class CartReadModel {

    private CartReadModel() {
    }

    /** 商品/SKU 条目状态：失效优先、调价次之、有效兜底；product 依赖故障为 UNKNOWN。 */
    public enum ItemStatus {
        /** 商品在售 + SKU 启用 + 价格与加购时一致。 */
        VALID,
        /** 商品已下架（productStatus≠ON_SALE）。 */
        PRODUCT_OFF_SHELF,
        /** 商品在售但 SKU 已禁用（skuStatus≠ENABLED）。 */
        SKU_INVALID,
        /** 商品或 SKU 已删除/不存在（product 批量占位缺失）。 */
        NOT_FOUND,
        /** 仍可买，但 product 最新价与 priceFenAtAdded 不一致；展示价以 priceFen 为准。 */
        PRICE_CHANGED,
        /** product 依赖故障，无法判定商品态（条目仍展示车中数量/选择/加购时快照价）。 */
        UNKNOWN
    }

    /** 库存三态 + 降级态；阈值口径对齐 CHG-0017（0 / 1..9 / ≥10）。 */
    public enum StockStatus {
        IN_STOCK,
        LOW_STOCK,
        OUT_OF_STOCK,
        /** inventory 依赖故障；按缺货口径排除合计但仍展示。 */
        UNKNOWN
    }

    /**
     * 读模型行。product 信息在 NOT_FOUND/UNKNOWN 等场景为 null；
     * priceFen 为 product 最新整数分（UNKNOWN 时 null）；priceFenAtAdded 来自车条目快照。
     */
    public record CartLine(long skuId, int quantity, boolean selected,
                           Long productId, String productName, String skuName,
                           Map<String, String> specs, String imageUrl,
                           Long priceFen, long priceFenAtAdded,
                           ItemStatus itemStatus, StockStatus stockStatus,
                           String createdAt, String updatedAt) {
    }

    /**
     * 读模型整车视图。
     *
     * @param selectedTotalFen 选中合计（整数分）：仅 VALID + selected + 库存 IN_STOCK/LOW_STOCK，
     *                         用 product 最新价计；失效/调价/缺货/未知库存均排除（AC-013）
     * @param selectedCount    勾选条目数（按车中 selected 标记计，与能否合计无关）
     */
    public record CartView(List<CartLine> items, long selectedTotalFen, int selectedCount) {

        public static CartView empty() {
            return new CartView(List.of(), 0L, 0);
        }
    }
}
