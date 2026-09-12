package com.ai.mall.identity.application.port;

public interface AuditRecorder {
    void record(long actor, String action, String targetType, String targetId, String result, String reason);
}
