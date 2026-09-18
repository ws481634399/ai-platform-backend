package com.ai.mall.system.domain.config;

import java.util.List;

/**
 * 配置变更历史仓储端口（CHG-0022，只追加）。
 */
public interface ConfigHistoryRepository {

    void insert(ConfigHistory history);

    /** 分页：type/key 可空（不过滤），按 id 倒序（最新在前）。 */
    List<ConfigHistory> findPage(ConfigTargetType type, String key, long offset, int limit);

    long count(ConfigTargetType type, String key);
}
