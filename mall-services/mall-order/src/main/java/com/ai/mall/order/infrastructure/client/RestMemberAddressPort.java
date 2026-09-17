package com.ai.mall.order.infrastructure.client;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.port.MemberAddressPort;
import com.ai.mall.order.domain.order.OrderErrorCode;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * mall-member 内部地址契约客户端（CHG-0019）：
 * GET /api/internal/members/{memberId}/addresses/{addressId}。
 *
 * <p>携带 {@code X-Internal-Token}；地址不存在/不归属（404）统一转为 empty，
 * 由订单侧转 B0405（不泄露归属差异）；连接失败/5xx/非法信封 → 503。
 */
@Component
public class RestMemberAddressPort implements MemberAddressPort {

    private final RestClient restClient;

    public RestMemberAddressPort(
            @Value("${mall.order.member-uri:http://localhost:8102}") String memberServiceUri,
            @Value("${mall.security.internal.shared-secret:dev-internal-secret}") String sharedSecret) {
        this.restClient = RestClient.builder()
                .baseUrl(memberServiceUri)
                .defaultHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                .build();
    }

    @Override
    public Optional<AddressSnapshot> findOwnedAddress(long memberId, long addressId) {
        UnifyResult<AddressView> result;
        try {
            result = restClient.get()
                    .uri("/api/internal/members/{memberId}/addresses/{addressId}", memberId, addressId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<UnifyResult<AddressView>>() {
                    });
        } catch (HttpClientErrorException.NotFound notFound) {
            // 不存在或不属于该会员：不区分两种情形
            return Optional.empty();
        } catch (RuntimeException ex) {
            throw new BusinessException(OrderErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            // 部分实现可能以失败信封返回 200：同样视为不存在
            return Optional.empty();
        }
        AddressView view = result.getData();
        return Optional.of(new AddressSnapshot(addressId, memberId, view.receiverName(), view.receiverPhone(),
                view.province(), view.city(), view.district(), view.detailAddress(), view.postalCode(),
                Boolean.TRUE.equals(view.defaultAddress())));
    }

    /** member 内部地址响应线框（地址 id/会员 id 已在路径中，响应只需快照字段）。 */
    record AddressView(String receiverName, String receiverPhone, String province, String city,
                       String district, String detailAddress, String postalCode, Boolean defaultAddress) {
    }
}
