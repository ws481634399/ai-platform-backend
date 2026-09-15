package com.ai.mall.product.infrastructure.client;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.product.interfaces.rest.mall.dto.MallSkuAvailabilityDtos.SkuAvailabilityRequest;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 库存可售查询客户端：调用 mall-inventory 内部批量 availability 接口。
 *
 * <p>CHG-0017 DU-BE-705：一次批量调用（无 N+1），携带 X-Internal-Token。
 * 调用失败（5xx/超时/网络异常）抛出，由调用方降级为 UNKNOWN。
 */
@Component
public class InventoryAvailabilityClient {

    private final RestClient restClient;

    public InventoryAvailabilityClient(
            @Value("${mall.product.inventory-service-uri:http://localhost:8106}") String inventoryServiceUri,
            @Value("${mall.security.internal.shared-secret:dev-internal-secret}") String sharedSecret) {
        this.restClient = RestClient.builder()
                .baseUrl(inventoryServiceUri)
                .defaultHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                .build();
    }

    /**
     * 批量查询可售数量，返回 skuId → availableQty。
     * inventory 未返回的 skuId 不在结果中（调用方按 0 处理）。
     *
     * @throws RuntimeException 网络/5xx/超时等任何异常（调用方降级）。
     */
    public Map<Long, Long> availability(List<Long> skuIds) {
        UnifyResult<java.util.List<AvailabilityItem>> result = restClient.post()
                .uri("/api/internal/inventory/availability")
                .body(new SkuAvailabilityRequest(skuIds))
                .retrieve()
                .body(new ParameterizedTypeReference<UnifyResult<java.util.List<AvailabilityItem>>>() {});
        if (result == null || result.getData() == null) {
            return Map.of();
        }
        Map<Long, Long> map = new java.util.HashMap<>();
        for (AvailabilityItem item : result.getData()) {
            map.put(item.skuId(), item.availableQty());
        }
        return map;
    }

    /** 内部 availability 响应项：精确数量（仅服务间）。 */
    public record AvailabilityItem(long skuId, long availableQty) {}
}
