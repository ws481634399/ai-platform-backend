package com.ai.mall.system.application.config;

import com.ai.mall.system.domain.config.ChangeKind;
import com.ai.mall.system.domain.config.ConfigException;
import com.ai.mall.system.domain.config.ConfigHistory;
import com.ai.mall.system.domain.config.ConfigHistoryRepository;
import com.ai.mall.system.domain.config.ConfigTargetType;
import com.ai.mall.system.domain.config.ConfigType;
import com.ai.mall.system.domain.config.EffectType;
import com.ai.mall.system.domain.config.SystemParameter;
import com.ai.mall.system.domain.config.SystemParameterRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统参数应用服务（CHG-0022）：强类型/范围校验、乐观锁、历史、缓存失效事件。
 */
@Service
public class SystemParameterAppService {

    private final SystemParameterRepository repository;
    private final ConfigHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SystemParameterAppService(SystemParameterRepository repository,
                                     ConfigHistoryRepository historyRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
    }

    // ---------- 命令 ----------

    /** 新建参数命令。 */
    public record CreateParameterCommand(String key, String name, String group, String type,
                                         String value, String defaultValue, String minValue, String maxValue,
                                         String effectType, boolean publicFlag, String description) {
    }

    /** 编辑参数命令（type/effectType 创建后不可变）。 */
    public record UpdateParameterCommand(String name, String group, String value, String defaultValue,
                                         String minValue, String maxValue, boolean publicFlag,
                                         String description, int version, String changeReason) {
    }

    // ---------- 查询 ----------

    @Transactional(readOnly = true)
    public SystemParameter getByKey(String key) {
        return repository.findByKey(key).orElseThrow(() -> ConfigException.notFound(key));
    }

    @Transactional(readOnly = true)
    public List<SystemParameter> page(String group, String type, int page, int size) {
        return repository.findPage(group, type, (long) (Math.max(1, page) - 1) * size, size);
    }

    @Transactional(readOnly = true)
    public long count(String group, String type) {
        return repository.count(group, type);
    }

    // ---------- 写入 ----------

    @Transactional
    public SystemParameter create(CreateParameterCommand command) {
        validateKeyName(command.key(), command.name());
        ConfigType type = parseType(command.type());
        EffectType effectType = parseEffectType(command.effectType());
        if (repository.findByKey(command.key()).isPresent()) {
            throw ConfigException.keyDuplicate(command.key());
        }
        String defaultValue = command.defaultValue() == null ? command.value() : command.defaultValue();
        SystemParameter parameter;
        try {
            parameter = SystemParameter.createNew(command.key().trim(), command.name().trim(), command.group(),
                    type, command.value(), defaultValue, command.minValue(), command.maxValue(),
                    effectType, command.publicFlag(), command.description(), Instant.now());
        } catch (IllegalArgumentException ex) {
            throw ConfigException.valueInvalid(ex.getMessage());
        }
        repository.insert(parameter);
        recordHistory(parameter.getKey(), null, parameter.getValue(), ChangeKind.CREATED, null);
        eventPublisher.publishEvent(ConfigChangedEvent.parameter(parameter.getKey()));
        return parameter;
    }

    @Transactional
    public SystemParameter update(String key, UpdateParameterCommand command) {
        validateKeyName(key, command.name());
        SystemParameter current = repository.findByKey(key).orElseThrow(() -> ConfigException.notFound(key));
        if (current.getVersion() != command.version()) {
            throw ConfigException.versionConflict(key);
        }
        SystemParameter modified;
        try {
            modified = current.applyEdit(command.name().trim(), command.group(), command.value(),
                    command.defaultValue(), command.minValue(), command.maxValue(),
                    command.publicFlag(), command.description());
        } catch (IllegalArgumentException ex) {
            throw ConfigException.valueInvalid(ex.getMessage());
        }
        if (!repository.casUpdate(modified, current.getVersion())) {
            throw ConfigException.versionConflict(key);
        }
        recordHistory(key, current.getValue(), modified.getValue(), ChangeKind.UPDATED,
                command.changeReason());
        eventPublisher.publishEvent(ConfigChangedEvent.parameter(key));
        return modified;
    }

    @Transactional
    public void delete(String key, String changeReason) {
        SystemParameter current = repository.findByKey(key).orElseThrow(() -> ConfigException.notFound(key));
        if (current.isBuiltIn()) {
            throw ConfigException.valueInvalid("内置配置不可删除: " + key);
        }
        repository.delete(current);
        recordHistory(key, current.getValue(), null, ChangeKind.DELETED, changeReason);
        eventPublisher.publishEvent(ConfigChangedEvent.parameter(key));
    }

    private void recordHistory(String key, String oldValue, String newValue, ChangeKind kind,
                               String changeReason) {
        ConfigHistory history = ConfigHistory.recorded(ConfigTargetType.PARAMETER, key,
                oldValue, newValue, kind, OperatorContext.currentOperator(), changeReason,
                OperatorContext.currentTraceId(), Instant.now());
        historyRepository.insert(history);
    }

    private static ConfigType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ConfigException.valueInvalid("parameterType 不能为空");
        }
        try {
            return ConfigType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ConfigException.valueInvalid("未知参数类型: " + raw);
        }
    }

    private static EffectType parseEffectType(String raw) {
        if (raw == null || raw.isBlank()) {
            return EffectType.DYNAMIC;
        }
        try {
            return EffectType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ConfigException.valueInvalid("未知生效方式: " + raw);
        }
    }

    private static void validateKeyName(String key, String name) {
        if (key == null || key.isBlank()) {
            throw ConfigException.valueInvalid("configKey 不能为空");
        }
        if (key.length() > 100) {
            throw ConfigException.valueInvalid("configKey 长度不能超过 100");
        }
        if (name == null || name.isBlank()) {
            throw ConfigException.valueInvalid("参数名称不能为空");
        }
    }
}
