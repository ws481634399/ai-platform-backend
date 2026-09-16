package com.ai.mall.cart.infrastructure.client;

import com.ai.mall.cart.application.cart.InventoryAvailabilityClient;
import com.ai.mall.cart.domain.cart.CartErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.common.web.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * mall-inventory 内部契约客户端（CHG-0018 DU-BE-802）。
 *
 * <p>直连 mall-inventory（默认 8106），携带 {@code X-Internal-Token} 调用
 * {@code POST /api/internal/inventory/availability}（CHG-0017 DU-BE-705），
 * 取回精确可售数量。任何传输故障、非 2xx、信封非法都归一为 503
 * {@link CartErrorCode#DEPENDENCY_UNAVAILABLE}，由读模型条目级降级，
 * 绝不让库存依赖故障导致整车白屏（AC-012）。响应 skuId 为字符串（@StringId）。
 */
@Component
public class RestInventoryAvailabilityClient implements InventoryAvailabilityClient {

    private static final String AVAILABILITY_PATH = "/api/internal/inventory/availability";

    private final RestClient restClient;

    public RestInventoryAvailabilityClient(
            @Value("${mall.cart.inventory-uri:http://localhost:8106}") String inventoryServiceUri,
            @Value("${mall.security.internal.shared-secret:dev-internal-secret}") String sharedSecret) {
        this.restClient = RestClient.builder()
                .baseUrl(inventoryServiceUri)
                .defaultHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                .build();
    }

    @Override
    public List<SkuAvailability> findAvailability(List<Long> skuIds) {
        Map<String, Object> body = Map.of("skuIds", skuIds);
        UnifyResult<List<ItemView>> result;
        try {
            result = restClient.post()
                    .uri(AVAILABILITY_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<UnifyResult<List<ItemView>>>() {
                    });
        } catch (RuntimeException ex) {
            throw new BusinessException(CartErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new BusinessException(CartErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            return result.getData().stream().map(RestInventoryAvailabilityClient::toAvailability).toList();
        } catch (RuntimeException ex) {
            throw new BusinessException(CartErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static SkuAvailability toAvailability(ItemView view) {
        Long skuId = parseLong(view.skuId());
        if (skuId == null) {
            throw new IllegalStateException("inventory availability 响应缺少 skuId");
        }
        Long qty = parseLong(view.availableQty());
        return new SkuAvailability(skuId, qty == null ? 0L : qty);
    }

    /** @StringId 字符串/数字混合兼容解析；null/空 → null。 */
    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : Long.parseLong(text);
    }

    /** inventory availability 响应线框视图：ID 字段允许字符串或数字。 */
    record ItemView(Object skuId, Object availableQty) {
    }
}
