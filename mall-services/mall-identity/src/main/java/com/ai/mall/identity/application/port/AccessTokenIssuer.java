package com.ai.mall.identity.application.port;

import com.ai.mall.common.security.SubjectType;
import java.time.Instant;

/**
 * 访问令牌签发端口（CHG-0016 泛化）：subjectType 参数化，
 * ADMIN 既有调用点显式传 {@link SubjectType#ADMIN}（claim 逐字节不变），
 * 会员链路传 {@link SubjectType#MEMBER}，JwtSubjectConverter 据 claim 生成 ROLE_&lt;TYPE&gt;。
 */
public interface AccessTokenIssuer {

    IssuedAccessToken issue(long subjectId, String username, long authVersion, SubjectType subjectType);

    record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
