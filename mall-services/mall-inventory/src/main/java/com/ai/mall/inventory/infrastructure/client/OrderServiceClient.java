package com.ai.mall.inventory.infrastructure.client;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.common.web.trace.TraceClientHttpRequestInterceptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * mall-order 内部 API 客户端（CHG-0025 M7 STORY-009-03-01）。
 *
 * <p>供消费乱序裁决回查订单真实状态、confirm/release 失败时登记订单补偿。
 * 与 SkuClient 同安全口径：X-Internal-Token + TraceId 透传。
 * 注意：传输类异常不吞——回查失败由消费侧触发 MQ 重试（保守不丢消息）。
 */
@Component
public class OrderServiceClient {

    private final RestClient restClient;

    public OrderServiceClient(@Value("${mall.inventory.order-uri:http://localhost:8105}") String orderUri,
                              @Value("${mall.security.internal.shared-secret:dev-internal-secret}") String sharedSecret) {
        this.restClient = RestClient.builder()
                .baseUrl(orderUri)
                .defaultHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                .requestInterceptor(TraceClientHttpRequestInterceptor.INSTANCE)
                .build();
    }

    /** 回查订单状态（PAID/CANCELLED/...）；不存在返回 404 异常，传输异常向上传播触发重试。 */
    public String getStatus(String orderId) {
        UnifyResult<StatusView> result = restClient.get()
                .uri("/api/internal/orders/{orderId}/status", orderId)
                .retrieve()
                .body(new ParameterizedTypeReference<UnifyResult<StatusView>>() {});
        return result == null || result.getData() == null ? null : result.getData().status();
    }

    /** 登记库存 confirm/release 补偿；异常向上传播。 */
    public void registerCompensation(CompensationRequest request) {
        restClient.post()
                .uri("/api/internal/compensations")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    /** 订单状态响应。 */
    public record StatusView(String orderId, String status) {
    }

    /** 补偿登记请求（字段对齐 mall-order CompensationInternalController）。 */
    public record CompensationRequest(String orderId, String orderNo, String type, String reason,
                                      List<Line> lines) {
    }

    /** 补偿载荷库存行。 */
    public record Line(long skuId, int quantity, String reservationId) {
    }
}
