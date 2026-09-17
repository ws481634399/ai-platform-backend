package com.ai.mall.order.infrastructure.client;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.port.InventoryPort;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * mall-inventory 内部契约客户端（CHG-0019）：availability/lock/release/confirm。
 *
 * <p>携带 {@code X-Internal-Token}；release/confirm 的库存侧幂等由库存服务以 reservation
 * 状态 CAS 保证（重复调用返回相同终态、不产生第二次数量变化）。
 * 错误归一：库存不足 B2204 → {@link OrderErrorCode#INSUFFICIENT_STOCK}（409，建单失败）；
 * 其余传输/信封/业务故障 → 503 {@link OrderErrorCode#DEPENDENCY_UNAVAILABLE}（可补偿重试）。
 */
@Component
public class RestInventoryPort implements InventoryPort {

    private static final String INSUFFICIENT_CODE = "B2204";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestInventoryPort(
            @Value("${mall.order.inventory-uri:http://localhost:8106}") String inventoryServiceUri,
            @Value("${mall.security.internal.shared-secret:dev-internal-secret}") String sharedSecret,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(inventoryServiceUri)
                .defaultHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Availability> availability(List<Long> skuIds) {
        UnifyResult<List<AvailabilityView>> result;
        try {
            result = restClient.post()
                    .uri("/api/internal/inventory/availability")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("skuIds", skuIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<UnifyResult<List<AvailabilityView>>>() {
                    });
        } catch (RuntimeException ex) {
            throw dependencyFailure();
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw dependencyFailure();
        }
        return result.getData().stream()
                .map(view -> new Availability(parseLong(view.skuId()), view.availableQty()))
                .toList();
    }

    @Override
    public void lock(String reservationId, long skuId, long quantity) {
        try {
            post("/api/internal/inventory/lock",
                    Map.of("reservationId", reservationId, "skuId", skuId, "quantity", quantity));
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw translate(ex);
        }
    }

    @Override
    public void release(String reservationId) {
        try {
            post("/api/internal/inventory/release", Map.of("reservationId", reservationId));
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw translate(ex);
        }
    }

    @Override
    public void confirm(String reservationId) {
        try {
            post("/api/internal/inventory/confirm", Map.of("reservationId", reservationId));
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw translate(ex);
        }
    }

    private void post(String path, Map<String, Object> body) {
        try {
            var result = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<UnifyResult<ReservationView>>() {
                    });
            if (result == null || !result.isSuccess()) {
                throw dependencyFailure();
            }
        } catch (HttpStatusCodeException ex) {
            throw translate(ex);
        }
    }

    /** 4xx/5xx 信封解读：库存不足按业务拒绝，其余按依赖故障。 */
    private RuntimeException translate(RuntimeException ex) {
        if (ex instanceof HttpStatusCodeException statusException) {
            String code = readEnvelopeCode(statusException);
            if (INSUFFICIENT_CODE.equals(code)) {
                return new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK, HttpStatus.CONFLICT);
            }
        }
        return dependencyFailure();
    }

    private String readEnvelopeCode(HttpStatusCodeException ex) {
        try {
            String raw = ex.getResponseBodyAsString();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return objectMapper.readTree(raw).path("code").asText(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long parseLong(Object value) {
        return Long.parseLong(value == null ? "" : value.toString().trim());
    }

    private static BusinessException dependencyFailure() {
        return new BusinessException(OrderErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** availability 响应线框：skuId 为 @StringId 字符串，availableQty 为数字。 */
    record AvailabilityView(Object skuId, long availableQty) {
    }

    /** lock/release/confirm 响应线框（仅校验信封成功，字段不落业务）。 */
    record ReservationView(String reservationId, Object skuId, long quantity, String status) {
    }
}
