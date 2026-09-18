package com.ai.mall.common.config;

/**
 * 系统参数快照（Redis/HTTP 跨进程 JSON 契约，CHG-0022 §2.3 冻结）。
 *
 * @param key     参数键
 * @param value   字符串形态的参数值（由消费方按 type 解析）
 * @param type    参数类型 STRING/INTEGER/LONG/DECIMAL/BOOLEAN/JSON
 * @param version 配置版本
 */
public record ParameterSnapshot(String key, String value, String type, long version) {
}
