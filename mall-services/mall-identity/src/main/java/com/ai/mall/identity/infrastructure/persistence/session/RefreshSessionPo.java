package com.ai.mall.identity.infrastructure.persistence.session;
import java.time.Instant;
public record RefreshSessionPo(String digest,String familyId,long adminId,long authVersion,Instant expiresAt,Instant usedAt,Instant revokedAt){}
