package com.ai.mall.identity.application.port;
import java.time.Instant;
public interface AccessTokenIssuer { IssuedAccessToken issue(long adminId,String username,long authVersion); record IssuedAccessToken(String value,Instant expiresAt){} }
