package com.ai.mall.system.interfaces.rest.admin.dto;

/** 配置变更历史视图（CHG-0022）；时间为 epoch millis。 */
public record HistoryView(String configType, String configKey, String oldValue, String newValue,
                          String changeKind, String changedBy, String changeReason, String traceId,
                          long changedAt) {
}
