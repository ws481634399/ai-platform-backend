package com.ai.mall.system.application.config;

import com.ai.mall.system.domain.config.ChangeKind;
import com.ai.mall.system.domain.config.ConfigException;
import com.ai.mall.system.domain.config.ConfigHistory;
import com.ai.mall.system.domain.config.ConfigHistoryRepository;
import com.ai.mall.system.domain.config.ConfigTargetType;
import com.ai.mall.system.domain.config.FeatureConfig;
import com.ai.mall.system.domain.config.FeatureConfigRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 功能开关应用服务（CHG-0022）：分页查询、创建、编辑（含启停）、删除；
 * 所有变更与历史同事务，写后发布 {@link ConfigChangedEvent}。
 */
@Service
public class FeatureConfigAppService {

    private final FeatureConfigRepository repository;
    private final ConfigHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FeatureConfigAppService(FeatureConfigRepository repository,
                                   ConfigHistoryRepository historyRepository,
                                   ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
    }

    // ---------- 命令 ----------

    /** 新建开关命令；管理端新建项 builtIn=false。 */
    public record CreateFeatureCommand(String key, String name, String group, boolean enabled,
                                       boolean publicFlag, String description) {
    }

    /** 编辑命令（含启停）；version 为客户端读到的当前版本。 */
    public record UpdateFeatureCommand(String name, String group, boolean enabled, boolean publicFlag,
                                       String description, int version, String changeReason) {
    }

    // ---------- 查询 ----------

    @Transactional(readOnly = true)
    public FeatureConfig getByKey(String key) {
        return repository.findByKey(key).orElseThrow(() -> ConfigException.notFound(key));
    }

    @Transactional(readOnly = true)
    public List<FeatureConfig> page(String group, Boolean enabled, int page, int size) {
        return repository.findPage(group, enabled, (long) (Math.max(1, page) - 1) * size, size);
    }

    @Transactional(readOnly = true)
    public long count(String group, Boolean enabled) {
        return repository.count(group, enabled);
    }

    // ---------- 写入 ----------

    @Transactional
    public FeatureConfig create(CreateFeatureCommand command) {
        validateKeyName(command.key(), command.name());
        if (repository.findByKey(command.key()).isPresent()) {
            throw ConfigException.keyDuplicate(command.key());
        }
        FeatureConfig config = FeatureConfig.createNew(command.key().trim(), command.name().trim(),
                command.group(), command.enabled(), command.publicFlag(), command.description(), Instant.now());
        repository.insert(config);
        recordHistory(config.getKey(), null, String.valueOf(config.isEnabled()), ChangeKind.CREATED, null);
        eventPublisher.publishEvent(ConfigChangedEvent.feature(config.getKey()));
        return config;
    }

    @Transactional
    public FeatureConfig update(String key, UpdateFeatureCommand command) {
        validateKeyName(key, command.name());
        FeatureConfig current = repository.findByKey(key).orElseThrow(() -> ConfigException.notFound(key));
        if (current.getVersion() != command.version()) {
            throw ConfigException.versionConflict(key);
        }
        boolean oldEnabled = current.isEnabled();
        FeatureConfig modified = current.applyEdit(command.name().trim(), command.group(),
                command.enabled(), command.publicFlag(), command.description());
        if (!repository.casUpdate(modified, current.getVersion())) {
            throw ConfigException.versionConflict(key);
        }
        recordHistory(key, String.valueOf(oldEnabled), String.valueOf(modified.isEnabled()),
                ChangeKind.UPDATED, command.changeReason());
        eventPublisher.publishEvent(ConfigChangedEvent.feature(key));
        return modified;
    }

    @Transactional
    public void delete(String key, String changeReason) {
        FeatureConfig current = repository.findByKey(key).orElseThrow(() -> ConfigException.notFound(key));
        if (current.isBuiltIn()) {
            throw ConfigException.valueInvalid("内置配置不可删除: " + key);
        }
        repository.delete(current);
        recordHistory(key, String.valueOf(current.isEnabled()), null, ChangeKind.DELETED, changeReason);
        eventPublisher.publishEvent(ConfigChangedEvent.feature(key));
    }

    private void recordHistory(String key, String oldValue, String newValue, ChangeKind kind,
                               String changeReason) {
        ConfigHistory history = ConfigHistory.recorded(ConfigTargetType.FEATURE, key,
                oldValue, newValue, kind, OperatorContext.currentOperator(), changeReason,
                OperatorContext.currentTraceId(), Instant.now());
        historyRepository.insert(history);
    }

    private static void validateKeyName(String key, String name) {
        if (key == null || key.isBlank()) {
            throw ConfigException.valueInvalid("configKey 不能为空");
        }
        if (key.length() > 100) {
            throw ConfigException.valueInvalid("configKey 长度不能超过 100");
        }
        if (name == null || name.isBlank()) {
            throw ConfigException.valueInvalid("功能名称不能为空");
        }
    }
}
