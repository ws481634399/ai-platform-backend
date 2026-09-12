package com.ai.mall.identity.audit;

import com.ai.mall.common.core.trace.TraceContext;
import com.ai.mall.identity.persistence.RbacCommandMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthAuditService {
    private final RbacCommandMapper mapper;
    public AuthAuditService(RbacCommandMapper mapper) { this.mapper = mapper; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long actor, String action, String targetType, String targetId, String result, String reasonCode) {
        String safeReason = reasonCode == null ? "{}" : "{\"reasonCode\":\"" + sanitize(reasonCode) + "\"}";
        mapper.audit(actor, action, targetType, targetId, result, TraceContext.get(), safeReason);
    }

    private static String sanitize(String value) { return value.replaceAll("[^A-Z0-9_-]", "_"); }
}
