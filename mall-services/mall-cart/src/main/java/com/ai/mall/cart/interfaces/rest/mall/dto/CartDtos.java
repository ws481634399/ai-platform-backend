package com.ai.mall.cart.interfaces.rest.mall.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 会员购物车接口契约（CHG-0018 DU-BE-801）。
 * 路径 SSOT：story-design §2 —— {@code /api/mall/cart}。
 * 雪花 ID 一律以 string 收发；金额为整数分。
 */
public final class CartDtos {

    private CartDtos() {
    }

    /** 加购请求。 */
    public record AddItemRequest(
            @NotBlank(message = "skuId 不能为空") String skuId,
            @NotNull(message = "quantity 不能为空")
            @Min(value = 1, message = "数量必须大于0")
            @Max(value = 999, message = "单品数量不能超过999件") Integer quantity) {
    }

    /** 改量请求。 */
    public record UpdateQuantityRequest(
            @NotNull(message = "quantity 不能为空")
            @Min(value = 1, message = "数量必须大于0")
            @Max(value = 999, message = "单品数量不能超过999件") Integer quantity) {
    }

    /** 批量删除请求。 */
    public record BatchDeleteRequest(
            @NotEmpty(message = "skuIds 不能为空")
            @Size(max = 100, message = "单次最多删除100个条目") List<String> skuIds) {
    }

    /** 购物车原始条目视图（DU-BE-802 将由商品/价/库存聚合视图替换）。 */
    public record CartItemView(String skuId, int quantity, boolean selected,
                               long priceFenAtAdded, String createdAt, String updatedAt) {
    }

    /** 购物车视图。 */
    public record CartView(List<CartItemView> items) {
    }

    /** M4 内部选中项条目。 */
    public record SelectedItemView(String skuId, int quantity) {
    }

    /** M4 内部选中项响应。 */
    public record SelectedItemsResponse(List<SelectedItemView> items) {
    }
}
