package com.ai.mall.cart.interfaces.rest.internal;

import com.ai.mall.cart.application.cart.CartApplicationService;
import com.ai.mall.cart.application.cart.CartApplicationService.SelectedItem;
import com.ai.mall.cart.interfaces.rest.mall.dto.CartDtos.SelectedItemView;
import com.ai.mall.cart.interfaces.rest.mall.dto.CartDtos.SelectedItemsResponse;
import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.web.exception.BusinessException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 购物车内部接口（CHG-0018 DU-BE-801，M4 下单结算消费）。
 *
 * <p>仅接受 {@code X-Internal-Token}（安全链 /api/internal/** hasRole SERVICE，
 * 任何 JWT 不放行）。返回指定会员当前勾选的 skuId/数量，不做价格与库存聚合
 * （结算侧另行实时核价核库存）。
 */
@RestController
@RequestMapping("/api/internal/carts")
public class InternalCartController {

    private final CartApplicationService carts;

    public InternalCartController(CartApplicationService carts) {
        this.carts = carts;
    }

    @GetMapping("/members/{memberId}/selected-items")
    public UnifyResult<SelectedItemsResponse> selectedItems(@PathVariable("memberId") long memberId) {
        if (memberId <= 0) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, HttpStatus.BAD_REQUEST, "memberId 不合法");
        }
        List<SelectedItemView> items = carts.selectedItems(memberId).stream()
                .map(item -> new SelectedItemView(Long.toString(item.skuId()), item.quantity()))
                .toList();
        return UnifyResult.ok(new SelectedItemsResponse(items));
    }

    /**
     * CHG-0019 REQ-M4-001：CART 来源下单成功后，订单侧按 skuId 批量删除会员购物车行。
     * 幂等：不存在的条目忽略；空列表为 no-op。
     */
    @DeleteMapping("/members/{memberId}/items")
    public UnifyResult<Void> deleteItems(@PathVariable("memberId") long memberId,
                                         @RequestBody BatchDeleteRequest request) {
        if (memberId <= 0) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, HttpStatus.BAD_REQUEST, "memberId 不合法");
        }
        if (request == null || request.skuIds() == null || request.skuIds().isEmpty()) {
            return UnifyResult.ok();
        }
        List<Long> skuIds;
        try {
            skuIds = request.skuIds().stream().map(Long::parseLong).toList();
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, HttpStatus.BAD_REQUEST, "skuId 不合法");
        }
        if (skuIds.stream().anyMatch(id -> id <= 0)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, HttpStatus.BAD_REQUEST, "skuId 不合法");
        }
        carts.removeBatch(memberId, skuIds);
        return UnifyResult.ok();
    }

    /** 批量删除请求（雪花 ID 以字符串传输）。 */
    public record BatchDeleteRequest(List<String> skuIds) {
    }
}
