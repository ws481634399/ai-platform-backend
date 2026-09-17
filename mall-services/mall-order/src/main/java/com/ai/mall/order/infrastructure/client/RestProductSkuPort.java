package com.ai.mall.order.infrastructure.client;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.port.ProductSkuPort;
import com.ai.mall.order.domain.order.OrderErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * mall-product 内部契约客户端（CHG-0019）：POST /api/internal/products/skus/batch。
 *
 * <p>携带 {@code X-Internal-Token}；连接失败/超时/5xx/4xx/非法信封一律归一为
 * 503 {@link OrderErrorCode#DEPENDENCY_UNAVAILABLE}，与"商品业务不可售"严格区分。
 * 响应 ID 可能是字符串或数字，null 安全转回 Long。
 */
@Component
public class RestProductSkuPort implements ProductSkuPort {

    private static final String BATCH_PATH = "/api/internal/products/skus/batch";

    private final RestClient restClient;

    public RestProductSkuPort(
            @Value("${mall.order.product-uri:http://localhost:8103}") String productServiceUri,
            @Value("${mall.security.internal.shared-secret:dev-internal-secret}") String sharedSecret) {
        this.restClient = RestClient.builder()
                .baseUrl(productServiceUri)
                .defaultHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                .build();
    }

    @Override
    public List<SkuSnapshot> batchSnapshots(List<Long> skuIds) {
        UnifyResult<List<ItemView>> result;
        try {
            result = restClient.post()
                    .uri(BATCH_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("skuIds", skuIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<UnifyResult<List<ItemView>>>() {
                    });
        } catch (RuntimeException ex) {
            throw dependencyFailure();
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw dependencyFailure();
        }
        try {
            return result.getData().stream().map(RestProductSkuPort::toSnapshot).toList();
        } catch (RuntimeException ex) {
            throw dependencyFailure();
        }
    }

    private static SkuSnapshot toSnapshot(ItemView view) {
        return new SkuSnapshot(parseLong(view.productId()), view.productName(), view.productStatus(),
                parseLong(view.skuId()), view.skuCode(), view.skuStatus(), parseLong(view.salePriceInCents()),
                view.mainImageUrl(),
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

    private static BusinessException dependencyFailure() {
        return new BusinessException(OrderErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** product batch 响应线框视图：ID 字段允许字符串或数字。 */
    record ItemView(Object productId, String productName, String productStatus,
                    Object skuId, String skuCode, String skuStatus,
                    Object salePriceInCents, String mainImageUrl,
                    Map<String, String> specifications, Boolean salable) {
    }
}
