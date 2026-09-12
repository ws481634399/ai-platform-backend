package com.ai.mall.identity.infrastructure.persistence.audit;

import com.ai.mall.common.core.trace.TraceContext;
import com.ai.mall.identity.application.port.AuditRecorder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

@Component
public class MyBatisAuditRecorder implements AuditRecorder {
    private final AuditMapper mapper;
    public MyBatisAuditRecorder(AuditMapper mapper) { this.mapper = mapper; }
    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void record(long actor, String action, String targetType, String targetId, String result, String reason) {
        mapper.insert(actor, action, targetType, targetId, result, TraceContext.get(), reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}");
    }
    @Mapper
    public interface AuditMapper {
        @Insert("INSERT INTO auth_audit_log(actor_admin_id,action,target_type,target_id,result,trace_id,detail_json) VALUES(#{actor},#{action},#{targetType},#{targetId},#{result},#{traceId},#{detail})")
        void insert(@Param("actor") long actor, @Param("action") String action, @Param("targetType") String targetType,
                    @Param("targetId") String targetId, @Param("result") String result,
                    @Param("traceId") String traceId, @Param("detail") String detail);
    }
}
