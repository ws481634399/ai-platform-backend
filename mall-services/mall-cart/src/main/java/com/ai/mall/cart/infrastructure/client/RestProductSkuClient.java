package com.ai.mall.cart.infrastructure.client;

import com.ai.mall.cart.application.cart.ProductSkuClient;
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
 * mall-product 内部契约客户端（CHG-0018 DU-BE-801）。
 *
 * <p>直连 mall-product（默认 8103），携带 {@code X-Internal-Token} 调用
 * {@code POST /api/internal/products/skus/batch} 一次取齐可售状态/价格。
 * 任何传输故障、非 2xx、信封非法都归一为 503 {@link CartErrorCode#DEPENDENCY_UNAVAILABLE}，
 * 与"商品业务不可售 400"严格区分；本客户端不发起任何库存相关调用（AC-014）。
 * 响应 ID 为字符串（@StringId），在此 null 安全地转回 Long。
 */
@Component
public class RestProductSkuClient implements ProductSkuClient {

    private static final String BATCH_PATH = "/api/internal/products/skus/batch";

    private final RestClient restClient;

    public RestProductSkuClient(
            @Value("${mall.cart.product-uri:http://localhost:8103}") String productServiceUri,
            @Value("${mall.security.internal.shared-secret:dev-internal-secret}") String sharedSecret) {
        this.restClient = RestClient.builder()
                .baseUrl(productServiceUri)
                .defaultHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                .build();
    }

    @Override
    public List<SkuSnapshot> findSnapshots(List<Long> skuIds) {
        Map<String, Object> body = Map.of("skuIds", skuIds);
        UnifyResult<List<ItemView>> result;
        try {
            result = restClient.post()
                    .uri(BATCH_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<UnifyResult<List<ItemView>>>() {
                    });
        } catch (RuntimeException ex) {
            // 连接拒绝/超时/5xx/4xx：依赖侧故障，绝不误判为"商品不可售"
            throw new BusinessException(CartErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new BusinessException(CartErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            return result.getData().stream().map(RestProductSkuClient::toSnapshot).toList();
        } catch (RuntimeException ex) {
            throw new BusinessException(CartErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static SkuSnapshot toSnapshot(ItemView view) {
        Long productId = parseLong(view.productId());
        Long skuId = parseLong(view.skuId());
        if (skuId == null) {
            throw new IllegalStateException("product sku/batch 响应缺少 skuId");
        }
        return new SkuSnapshot(productId, view.productName(), view.productStatus(), skuId, view.skuCode(),
                view.skuStatus(), parseLong(view.salePriceInCents()), view.mainImageUrl(),
                view.specifications() == null ? Map.of() : view.specifications(),
                Boolean.TRUE.equals(view.salable()));
    }

    /** @StringId 字符串/数字混合兼容解析；null/空 → null（不存在占位项）。 */
    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : Long.parseLong(text);
    }

    /** product batch 响应线框视图：ID 字段允许字符串或数字。 */
    record ItemView(Object productId, String productName, String productStatus,
                    Object skuId, String skuCode, String skuStatus,
                    Object salePriceInCents, String mainImageUrl,
                    Map<String, String> specifications, Boolean salable) {
    }
}
