package com.ai.mall.common.security;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.HashSet;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class JwtSubjectConverter implements Converter<Jwt, UsernamePasswordAuthenticationToken> {
    @Override
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        SubjectType type;
        try { type = SubjectType.valueOf(jwt.getClaimAsString("subject_type")); }
        catch (RuntimeException error) { throw new IllegalArgumentException("invalid subject_type", error); }
        if (type == SubjectType.GUEST) throw new IllegalArgumentException("GUEST cannot be an authenticated token subject");
        List<String> claim = jwt.getClaimAsStringList("permissions");
        Set<String> permissions = claim == null ? Set.of() : Set.copyOf(claim);
        var subject = new AuthenticatedSubject(jwt.getSubject(), jwt.getClaimAsString("username"), type,
                number(jwt.getClaim("auth_version")), permissions);
        var authorities = permissions.stream().map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(HashSet::new));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + type.name()));
        return new UsernamePasswordAuthenticationToken(subject, jwt, authorities);
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        throw new IllegalArgumentException("auth_version claim is required");
    }
}
