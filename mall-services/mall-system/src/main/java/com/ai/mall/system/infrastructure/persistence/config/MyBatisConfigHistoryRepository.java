package com.ai.mall.system.infrastructure.persistence.config;

import com.ai.mall.system.domain.config.ConfigHistory;
import com.ai.mall.system.domain.config.ConfigHistoryRepository;
import com.ai.mall.system.domain.config.ConfigTargetType;
import com.ai.mall.system.domain.config.ChangeKind;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 配置变更历史仓储 MyBatis-Plus 实现（CHG-0022，只追加）。 */
@Repository
public class MyBatisConfigHistoryRepository implements ConfigHistoryRepository {

    private final ConfigHistoryMapper mapper;

    public MyBatisConfigHistoryRepository(ConfigHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ConfigHistory history) {
        ConfigHistoryPo po = new ConfigHistoryPo();
        po.setConfigType(history.configType().name());
        po.setConfigKey(history.configKey());
        po.setOldValue(history.oldValue());
        po.setNewValue(history.newValue());
        po.setChangeKind(history.changeKind().name());
        po.setChangedBy(history.changedBy());
        po.setChangeReason(history.changeReason());
        po.setTraceId(history.traceId());
        po.setCreatedAt(history.createdAt());
        mapper.insert(po);
    }

    @Override
    public List<ConfigHistory> findPage(ConfigTargetType type, String key, long offset, int limit) {
        LambdaQueryWrapper<ConfigHistoryPo> wrapper = new LambdaQueryWrapper<ConfigHistoryPo>()
                .orderByDesc(ConfigHistoryPo::getId)
                .last("LIMIT " + Math.max(0, offset) + "," + Math.max(1, limit));
        if (type != null) {
            wrapper.eq(ConfigHistoryPo::getConfigType, type.name());
        }
        if (key != null && !key.isBlank()) {
            wrapper.eq(ConfigHistoryPo::getConfigKey, key);
        }
        return mapper.selectList(wrapper).stream().map(po -> new ConfigHistory(
                po.getId(), ConfigTargetType.valueOf(po.getConfigType()), po.getConfigKey(),
                po.getOldValue(), po.getNewValue(), ChangeKind.valueOf(po.getChangeKind()),
                po.getChangedBy(), po.getChangeReason(), po.getTraceId(), po.getCreatedAt())).toList();
    }

    @Override
    public long count(ConfigTargetType type, String key) {
        LambdaQueryWrapper<ConfigHistoryPo> wrapper = new LambdaQueryWrapper<>();
        if (type != null) {
            wrapper.eq(ConfigHistoryPo::getConfigType, type.name());
        }
        if (key != null && !key.isBlank()) {
            wrapper.eq(ConfigHistoryPo::getConfigKey, key);
        }
        return mapper.selectCount(wrapper);
    }
}
