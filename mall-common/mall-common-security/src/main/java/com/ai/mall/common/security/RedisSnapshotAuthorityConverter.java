package com.ai.mall.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 跨服务权限传播的 JWT 认证转换器（资源服务侧）。
 *
 * <p>身份、主体类型与认证版本仍由 {@link JwtSubjectConverter} 从已验签 JWT 解析；
 * 细粒度权限码不写入 Token（mall-identity 签发时只放 auth_version），
 * 资源服务凭主体 ID 读共享 Redis：先读 {@code authz:current:{adminId}} 指针取得最新
 * permissionVersion，再读 {@code authz:{adminId}:{permissionVersion}} 快照取权限码集合。
 *
 * <p>失败语义（fail-closed）：
 * <ul>
 *   <li>非 ADMIN 主体不查快照，返回仅含角色的认证（商城主体不使用本机制）；</li>
 *   <li>指针/快照缺失（尚未会话引导或已过 TTL）→ 401，由入口点引导重新登录/会话引导；</li>
 *   <li>快照内容损坏 → 删除坏键并 401；</li>
 *   <li>Redis 基础设施故障 → 透传运行时异常（500），不静默放行。</li>
 * </ul>
 */
public final class RedisSnapshotAuthorityConverter
        implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    private final JwtSubjectConverter identityConverter = new JwtSubjectConverter();
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisSnapshotAuthorityConverter(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        UsernamePasswordAuthenticationToken base = identityConverter.convert(jwt);
        if (!(base.getPrincipal() instanceof AuthenticatedSubject subject)) {
            throw new BadCredentialsException("invalid token subject");
        }
        // 商城会员等非管理员主体不读取管理端授权快照
        if (!subject.isAdmin()) {
            return base;
        }
        final long adminId;
        try {
            adminId = Long.parseLong(subject.subjectId());
        } catch (NumberFormatException error) {
            throw new BadCredentialsException("invalid admin subject id", error);
        }

        String currentKey = AuthorizationKeys.current(adminId);
        String versionText = redis.opsForValue().get(currentKey);
        if (versionText == null || versionText.isBlank()) {
            throw new BadCredentialsException("authorization snapshot pointer missing; session bootstrap required");
        }
        final long permissionVersion;
        try {
            permissionVersion = Long.parseLong(versionText.trim());
        } catch (NumberFormatException error) {
            redis.delete(currentKey);
            throw new BadCredentialsException("authorization snapshot pointer malformed", error);
        }

        String snapshotKey = AuthorizationKeys.snapshot(adminId, permissionVersion);
        String json = redis.opsForValue().get(snapshotKey);
        if (json == null || json.isBlank()) {
            // 指针存在但版本快照已过 TTL：清理悬挂指针并要求重新引导
            redis.delete(currentKey);
            throw new BadCredentialsException("authorization snapshot expired; session bootstrap required");
        }

        AuthoritySnapshot snapshot;
        try {
            snapshot = objectMapper.readValue(json, AuthoritySnapshot.class);
        } catch (Exception error) {
            redis.delete(snapshotKey);
            throw new BadCredentialsException("authorization snapshot unreadable", error);
        }

        Set<SimpleGrantedAuthority> authorities = snapshot.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(HashSet::new));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + subject.subjectType().name()));
        return new UsernamePasswordAuthenticationToken(subject, jwt, authorities);
    }
}
