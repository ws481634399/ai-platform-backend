package com.ai.mall.cart.interfaces.rest.mall;

import com.ai.mall.cart.application.cart.CartApplicationService;
import com.ai.mall.cart.domain.cart.CartItem;
import com.ai.mall.cart.interfaces.rest.mall.dto.CartDtos.AddItemRequest;
import com.ai.mall.cart.interfaces.rest.mall.dto.CartDtos.BatchDeleteRequest;
import com.ai.mall.cart.interfaces.rest.mall.dto.CartDtos.CartItemView;
import com.ai.mall.cart.interfaces.rest.mall.dto.CartDtos.CartView;
import com.ai.mall.cart.interfaces.rest.mall.dto.CartDtos.UpdateQuantityRequest;
import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.AuthenticatedSubject;
import com.ai.mall.common.security.SecurityContextFacade;
import com.ai.mall.common.web.exception.BusinessException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员购物车接口（CHG-0018 DU-BE-801）。
 *
 * <p>路径 SSOT：story-spec §4 / story-design §2 —— {@code /api/mall/cart}。
 * memberId 只从 {@link SecurityContextFacade} 取（sub=Long.toString(memberId)），
 * 请求体/路径中任何 memberId 均无入口；类级 ROLE_MEMBER 收口，
 * 路径层安全链 {@code /api/mall/**} 双重保险。
 */
@RestController
@RequestMapping("/api/mall/cart")
@PreAuthorize("hasRole('MEMBER')")
public class CartController {

    private final CartApplicationService carts;

    public CartController(CartApplicationService carts) {
        this.carts = carts;
    }

    /** 本 Story 返回原始条目视图；DU-BE-802 在此替换为商品/价/库存实时聚合。 */
    @GetMapping
    public UnifyResult<CartView> getCart() {
        return UnifyResult.ok(toView(carts.list(currentMemberId())));
    }

    @PostMapping("/items")
    public UnifyResult<CartView> addItem(@Valid @RequestBody AddItemRequest request) {
        List<CartItem> items = carts.add(currentMemberId(), parseSkuId(request.skuId()), request.quantity());
        return UnifyResult.ok(toView(items));
    }

    @PutMapping("/items/{skuId}")
    public UnifyResult<CartView> updateQuantity(@PathVariable("skuId") String skuId,
                                                @Valid @RequestBody UpdateQuantityRequest request) {
        List<CartItem> items = carts.updateQuantity(currentMemberId(), parseSkuId(skuId), request.quantity());
        return UnifyResult.ok(toView(items));
    }

    @DeleteMapping("/items/{skuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(@PathVariable("skuId") String skuId) {
        carts.remove(currentMemberId(), parseSkuId(skuId));
    }

    @PostMapping("/items/batch-delete")
    public UnifyResult<CartView> batchRemove(@Valid @RequestBody BatchDeleteRequest request) {
        List<Long> skuIds = request.skuIds().stream().map(CartController::parseSkuId).distinct().toList();
        return UnifyResult.ok(toView(carts.removeBatch(currentMemberId(), skuIds)));
    }

    @PostMapping("/items/{skuId}/select")
    public UnifyResult<CartView> selectOne(@PathVariable("skuId") String skuId) {
        return UnifyResult.ok(toView(carts.select(currentMemberId(), parseSkuId(skuId), true)));
    }

    @PostMapping("/items/{skuId}/unselect")
    public UnifyResult<CartView> unselectOne(@PathVariable("skuId") String skuId) {
        return UnifyResult.ok(toView(carts.select(currentMemberId(), parseSkuId(skuId), false)));
    }

    @PostMapping("/select-all")
    public UnifyResult<CartView> selectAll() {
        return UnifyResult.ok(toView(carts.selectAll(currentMemberId(), true)));
    }

    @PostMapping("/unselect-all")
    public UnifyResult<CartView> unselectAll() {
        return UnifyResult.ok(toView(carts.selectAll(currentMemberId(), false)));
    }

    /** 当前会话会员 ID：仅取自安全上下文（sub=Long.toString(memberId)），无入参来源。 */
    private static long currentMemberId() {
        AuthenticatedSubject subject = SecurityContextFacade.currentSubject()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        HttpStatus.UNAUTHORIZED, "请先登录"));
        try {
            return Long.parseLong(subject.subjectId());
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    HttpStatus.UNAUTHORIZED, "登录主体非法，请重新登录");
        }
    }

    /** 雪花 ID 线框为字符串；非法值统一参数错误，不带内部细节。 */
    private static long parseSkuId(String raw) {
        try {
            long skuId = Long.parseLong(raw == null ? "" : raw.trim());
            if (skuId <= 0) {
                throw new NumberFormatException("non-positive skuId");
            }
            return skuId;
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, HttpStatus.BAD_REQUEST, "skuId 不合法");
        }
    }

    private static CartView toView(List<CartItem> items) {
        List<CartItemView> views = items.stream()
                .map(item -> new CartItemView(Long.toString(item.skuId()), item.quantity(), item.selected(),
                        item.priceFenAtAdded(), item.createdAt(), item.updatedAt()))
                .toList();
        return new CartView(views);
    }
}
