package com.ai.mall.order.infrastructure.client;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.port.CartSelectionPort;
import com.ai.mall.order.domain.order.OrderErrorCode;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * mall-cart 内部契约客户端（CHG-0019）：
 * GET /api/internal/carts/members/{memberId}/selected-items。
 *
 * <p>仅 CART 来源预览使用；传输/信封故障 → 503。
 */
@Component
public class RestCartSelectionPort implements CartSelectionPort {

    private static final Logger log = LoggerFactory.getLogger(RestCartSelectionPort.class);

    private final RestClient restClient;

    public RestCartSelectionPort(
            @Value("${mall.order.cart-uri:http://localhost:8104}") String cartServiceUri,
            @Value("${mall.security.internal.shared-secret:dev-internal-secret}") String sharedSecret) {
        this.restClient = RestClient.builder()
                .baseUrl(cartServiceUri)
                .defaultHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                .build();
    }

    @Override
    public List<SelectedItem> selectedItems(long memberId) {
        UnifyResult<SelectedItemsView> result;
        try {
            result = restClient.get()
                    .uri("/api/internal/carts/members/{memberId}/selected-items", memberId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<UnifyResult<SelectedItemsView>>() {
                    });
        } catch (RuntimeException ex) {
            throw new BusinessException(OrderErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (result == null || !result.isSuccess() || result.getData() == null
                || result.getData().items() == null) {
            throw new BusinessException(OrderErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        return result.getData().items().stream()
                .map(view -> new SelectedItem(Long.parseLong(view.skuId()), view.quantity()))
                .toList();
    }

    @Override
    public void deleteItems(long memberId, List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        try {
            // mall-cart 内部批量删除契约：DELETE /api/internal/carts/members/{memberId}/items
            // DELETE 携带请求体需走 method(HttpMethod.DELETE)（RestClient.delete() 无 body 能力）
            restClient.method(org.springframework.http.HttpMethod.DELETE)
                    .uri("/api/internal/carts/members/{memberId}/items", memberId)
                    .body(new DeleteRequest(skuIds.stream().map(String::valueOf).toList()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            // 购物车清理失败不影响订单：WARN 记录，由会员侧重新勾选/手动清理兜底
            log.warn("订单创建后清理购物车失败 memberId={}, skuIds={}", memberId, skuIds, ex);
        }
    }

    record DeleteRequest(List<String> skuIds) {
    }

    record SelectedItemsView(List<ItemView> items) {
    }

    record ItemView(String skuId, int quantity) {
    }
}
