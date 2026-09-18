package com.ai.mall.system.application.config;

import com.ai.mall.system.domain.config.ConfigHistory;
import com.ai.mall.system.domain.config.ConfigHistoryRepository;
import com.ai.mall.system.domain.config.ConfigTargetType;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配置变更历史查询应用服务（CHG-0022，只读、只追加）。
 */
@Service
public class ConfigHistoryAppService {

    private final ConfigHistoryRepository repository;

    public ConfigHistoryAppService(ConfigHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ConfigHistory> page(ConfigTargetType type, String key, int page, int size) {
        return repository.findPage(type, key, (long) (Math.max(1, page) - 1) * size, size);
    }

    @Transactional(readOnly = true)
    public long count(ConfigTargetType type, String key) {
        return repository.count(type, key);
    }
}
