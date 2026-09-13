package com.ai.mall.inventory.infrastructure.client;

import com.ai.mall.common.core.result.UnifyResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * SKU 契约校验客户端：调用 mall-product 内部接口校验 SKU 存在。
 */
@Component
public class SkuClient {

    private final RestClient restClient;

    public SkuClient(@Value("${mall.inventory.sku-service-uri:http://localhost:8103}") String skuServiceUri) {
        this.restClient = RestClient.builder().baseUrl(skuServiceUri).build();
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
}
