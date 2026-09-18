package com.ai.mall.inventory.infrastructure.client;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.InternalIdentityFilter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * SKU 契约校验客户端：调用 mall-product 内部接口校验 SKU 存在。
 *
 * <p>CHG-0015：服务间调用携带 X-Internal-Token 共享凭证。
 */
@Component
public class SkuClient {

    private final RestClient restClient;

    public SkuClient(@Value("${mall.inventory.sku-service-uri:http://localhost:8103}") String skuServiceUri,
                     @Value("${mall.security.internal.shared-secret:dev-internal-secret}") String sharedSecret) {
        this.restClient = RestClient.builder()
                .baseUrl(skuServiceUri)
                .defaultHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                .build();
    }

    public boolean exists(long skuId) {
        try {
            UnifyResult<Boolean> result = restClient.get()
                    .uri("/api/internal/products/skus/{skuId}", skuId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<UnifyResult<Boolean>>() {});
            return result != null && Boolean.TRUE.equals(result.getData());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 批量拉取 SKU 展示信息（商品名/编码/规格/主图），用于管理端库存列表富化。
     * 复用商品服务内部批量快照契约 POST /api/internal/products/skus/batch；
     * 任何异常降级为空映射（列表字段展示为“—”，不影响库存主数据）。
     */
    public Map<Long, SkuInfo> batchInfo(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        try {
            UnifyResult<List<SkuInfoView>> result = restClient.post()
                    .uri("/api/internal/products/skus/batch")
                    .body(Map.of("skuIds", skuIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<UnifyResult<List<SkuInfoView>>>() {});
            if (result == null || result.getData() == null) {
                return Map.of();
            }
            Map<Long, SkuInfo> map = new java.util.LinkedHashMap<>();
            for (SkuInfoView view : result.getData()) {
                if (view.skuId() == null || view.skuId().isBlank()) {
                    continue;
                }
                map.put(Long.parseLong(view.skuId()), new SkuInfo(
                        view.productName(), view.skuCode(), view.specifications(), view.mainImageUrl()));
            }
            return map;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 内部批量快照响应项（雪花 ID 出参为字符串，JS 精度保护）。 */
    public record SkuInfoView(String productId, String productName, String productStatus,
                              String skuId, String skuCode, String skuStatus,
                              Long salePriceInCents, String mainImageUrl,
                              Map<String, String> specifications, boolean salable) {
    }

    /** 管理端列表展示所需的 SKU 信息子集。 */
    public record SkuInfo(String productName, String skuCode,
                          Map<String, String> specifications, String mainImageUrl) {
    }
}
