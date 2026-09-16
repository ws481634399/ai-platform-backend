package com.ai.mall.cart.interfaces.rest.mall.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 会员购物车接口契约（CHG-0018 DU-BE-801/802）。
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

    /** 购物车写操作回显的原始条目视图（车条目存储字段，不含实时聚合）。 */
    public record CartItemView(String skuId, int quantity, boolean selected,
                               long priceFenAtAdded, String createdAt, String updatedAt) {
    }

    /** 写操作响应：原始车视图。 */
    public record CartView(List<CartItemView> items) {
    }

    /**
     * 读模型行（DU-BE-802）：商品/最新价/库存均为读车实时聚合。
     * productId/priceFen 在 NOT_FOUND/UNKNOWN 等降级场景可 null；
     * specs 为规格名值对（与 product sku/batch 同形）；skuName 承载 product skuCode。
     */
    public record CartLineView(String skuId, int quantity, boolean selected,
                               String productId, String productName, String skuName,
                               Map<String, String> specs, String imageUrl,
                               Long priceFen, long priceFenAtAdded,
                               String itemStatus, String stockStatus,
                               String createdAt, String updatedAt) {
    }

    /** 读模型整车视图：恒 200（含降级条目）；金额仅展示，结算价 M4 重算。 */
    public record CartReadView(List<CartLineView> items, long selectedTotalFen, int selectedCount) {
    }

    /** M4 内部选中项条目。 */
    public record SelectedItemView(String skuId, int quantity) {
    }

    /** M4 内部选中项响应。 */
    public record SelectedItemsResponse(List<SelectedItemView> items) {
    }

    /** 游客车合并条目（CHG-0018 DU-BE-803）。 */
    public record GuestCartItemDto(
            @NotBlank(message = "skuId 不能为空") String skuId,
            @NotNull(message = "quantity 不能为空")
            @Min(value = 1, message = "数量必须大于0")
            @Max(value = 999, message = "单品数量不能超过999件") Integer quantity,
            Boolean selected) {
        public boolean selectedOrDefault() {
            return selected == null || selected;
        }
    }

    /** 合并请求：一次性 token + 游客车条目（≤100）。 */
    public record MergeCartRequest(
            @NotBlank(message = "mergeToken 不能为空") String mergeToken,
            @NotEmpty(message = "items 不能为空")
            @Size(max = 100, message = "单次最多合并100个条目") List<GuestCartItemDto> items) {
    }

    /** 合并 token 响应。 */
    public record MergeTokenResponse(String mergeToken, long expiresIn) {
    }

    /** 合并结果响应：merged 成功并入；truncated 超 999 截断；dropped 失效/超 100 丢弃。 */
    public record MergeCartResponse(
            List<MergedSkuView> merged,
            List<TruncatedSkuView> truncated,
            List<DroppedSkuView> dropped) {
    }

    public record MergedSkuView(String skuId, int quantity) {
    }

    public record TruncatedSkuView(String skuId, int finalQuantity) {
    }

    public record DroppedSkuView(String skuId, String reason) {
    }
}
