package com.ai.mall.identity.infrastructure.client;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.identity.application.port.MemberProvisioner;
import com.ai.mall.identity.domain.model.member.MemberRegisteredEvent;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 会员开通投递客户端（CHG-0016）：identity → mall-member 服务间调用。
 *
 * <p>携带 {@code X-Internal-Token} 共享凭证（CHG-0015 机制）；
 * 契约：POST /api/internal/members/provision，事件载荷 memberId 以字符串传输。
 * 对端返回 200 即视为投递成功（provisioned=false 的幂等重放也算成功 → outbox DONE）；
 * 连接失败/4xx/5xx/信封 success=false 一律抛异常交 relay 重试。
 */
@Component
public class MemberProvisionClient implements MemberProvisioner {

    private static final String PROVISION_PATH = "/api/internal/members/provision";

    private final RestClient restClient;

    public MemberProvisionClient(
            @Value("${mall.member.service-uri:http://localhost:8102}") String memberServiceUri,
            @Value("${mall.security.internal.shared-secret:dev-internal-secret}") String sharedSecret) {
        this.restClient = RestClient.builder()
                .baseUrl(memberServiceUri)
                .defaultHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                .build();
    }

    @Override
    public void provision(MemberRegisteredEvent event) {
        UnifyResult<ProvisionAck> result = restClient.post()
                .uri(PROVISION_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProvisionRequest(event.eventId(), Long.toString(event.memberId()),
                        event.username(), event.nickname(), event.occurredAt()))
                .retrieve()
                .body(new ParameterizedTypeReference<UnifyResult<ProvisionAck>>() {});
        if (result == null || !result.isSuccess()) {
            // 不含事件外的敏感信息；eventId 可安全记录
            throw new IllegalStateException("member provision rejected for event " + event.eventId());
        }
    }

    /** provision 请求体（对端 InternalMemberProvisionController 契约）。 */
    record ProvisionRequest(String eventId, String memberId, String username, String nickname,
                            Instant occurredAt) {
    }

    /** provision 响应数据：provisioned=false 表示幂等重放，投递本身仍成功。 */
    record ProvisionAck(boolean provisioned) {
    }
}
