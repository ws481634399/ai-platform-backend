package com.ai.mall.member.infrastructure.client;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.member.application.member.MemberProfileErrorCode;
import com.ai.mall.member.application.port.IdentityProfileSeedClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * identity profile-seed 客户端（CHG-0016 STORY-003-01-02-01）：member → identity 服务间反向调用。
 *
 * <p>携带 {@code X-Internal-Token}（DU-BE-501 共享凭证），直连 identity 8101；
 * 契约：{@code GET /api/internal/members/{memberId}/profile-seed} → UnifyResult 信封，
 * data.memberId 为 @StringId 字符串（Jackson 可直接入 long）。
 */
@Component
public class RestIdentityProfileSeedClient implements IdentityProfileSeedClient {

    private static final String SEED_PATH = "/api/internal/members/{memberId}/profile-seed";

    private final RestClient restClient;

    public RestIdentityProfileSeedClient(
            @Value("${mall.identity.service-uri:http://localhost:8101}") String identityServiceUri,
            @Value("${mall.security.internal.shared-secret:dev-internal-secret}") String sharedSecret) {
        this.restClient = RestClient.builder()
                .baseUrl(identityServiceUri)
                .defaultHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                .build();
    }

    @Override
    public ProfileSeed fetchSeed(long memberId) {
        UnifyResult<SeedView> result;
        try {
            result = restClient.get()
                    .uri(SEED_PATH, memberId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<UnifyResult<SeedView>>() {
                    });
        } catch (HttpClientErrorException.NotFound
                 | HttpClientErrorException.Unauthorized ex) {
            // 认证有效但 identity 无账号 / 内部凭证失效：认证与业务数据不一致，强制重新登录
            throw new BusinessException(MemberProfileErrorCode.PROFILE_INCONSISTENT, HttpStatus.UNAUTHORIZED);
        } catch (HttpClientErrorException ex) {
            throw new BusinessException(MemberProfileErrorCode.PROFILE_SEED_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE);
        } catch (RuntimeException ex) {
            // 连接拒绝/超时（ResourceAccessException）等：依赖暂不可用
            throw new BusinessException(MemberProfileErrorCode.PROFILE_SEED_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new BusinessException(MemberProfileErrorCode.PROFILE_SEED_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        SeedView seed = result.getData();
        return new ProfileSeed(Long.parseLong(seed.memberId()), seed.username(), seed.status());
    }

    /** identity 种子视图（与 InternalMemberController.ProfileSeedResponse 对齐）。 */
    record SeedView(String memberId, String username, String status) {
    }
}
