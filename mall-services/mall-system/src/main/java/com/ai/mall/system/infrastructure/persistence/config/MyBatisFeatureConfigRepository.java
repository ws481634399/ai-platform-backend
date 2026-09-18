package com.ai.mall.system.infrastructure.persistence.config;

import com.ai.mall.system.domain.config.FeatureConfig;
import com.ai.mall.system.domain.config.FeatureConfigRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 功能开关仓储 MyBatis-Plus 实现（CHG-0022）。 */
@Repository
public class MyBatisFeatureConfigRepository implements FeatureConfigRepository {

    private final FeatureConfigMapper mapper;

    public MyBatisFeatureConfigRepository(FeatureConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<FeatureConfig> findByKey(String key) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<FeatureConfigPo>()
                .eq(FeatureConfigPo::getConfigKey, key)))
                .map(MyBatisFeatureConfigRepository::toDomain);
    }

    @Override
    public List<FeatureConfig> findPage(String group, Boolean enabled, long offset, int limit) {
        LambdaQueryWrapper<FeatureConfigPo> wrapper = new LambdaQueryWrapper<FeatureConfigPo>()
                .orderByAsc(FeatureConfigPo::getId)
                .last("LIMIT " + Math.max(0, offset) + "," + Math.max(1, limit));
        if (group != null && !group.isBlank()) {
            wrapper.eq(FeatureConfigPo::getConfigGroup, group);
        }
        if (enabled != null) {
            wrapper.eq(FeatureConfigPo::getEnabled, enabled ? 1 : 0);
        }
        return mapper.selectList(wrapper).stream()
                .map(MyBatisFeatureConfigRepository::toDomain).toList();
    }

    @Override
    public long count(String group, Boolean enabled) {
        LambdaQueryWrapper<FeatureConfigPo> wrapper = new LambdaQueryWrapper<>();
        if (group != null && !group.isBlank()) {
            wrapper.eq(FeatureConfigPo::getConfigGroup, group);
        }
        if (enabled != null) {
            wrapper.eq(FeatureConfigPo::getEnabled, enabled ? 1 : 0);
        }
        return mapper.selectCount(wrapper);
    }

    @Override
    public List<FeatureConfig> findAllPublic() {
        return mapper.selectList(new LambdaQueryWrapper<FeatureConfigPo>()
                        .eq(FeatureConfigPo::getPublicFlag, 1)
                        .orderByAsc(FeatureConfigPo::getId))
                .stream().map(MyBatisFeatureConfigRepository::toDomain).toList();
    }

    @Override
    public void insert(FeatureConfig config) {
        FeatureConfigPo po = toPo(config);
        mapper.insert(po);
        config.assignId(po.getId());
    }

    @Override
    public boolean casUpdate(FeatureConfig modified, int expectedVersion) {
        return mapper.update(null, new LambdaUpdateWrapper<FeatureConfigPo>()
                .eq(FeatureConfigPo::getId, modified.getId())
                .eq(FeatureConfigPo::getVersion, expectedVersion)
                .set(FeatureConfigPo::getFeatureName, modified.getName())
                .set(FeatureConfigPo::getConfigGroup, modified.getGroup())
                .set(FeatureConfigPo::getEnabled, modified.isEnabled() ? 1 : 0)
                .set(FeatureConfigPo::getPublicFlag, modified.isPublicFlag() ? 1 : 0)
                .set(FeatureConfigPo::getDescription, modified.getDescription())
                .set(FeatureConfigPo::getVersion, modified.getVersion())
                .set(FeatureConfigPo::getUpdatedAt, modified.getUpdatedAt())) > 0;
    }

    @Override
    public void delete(FeatureConfig config) {
        mapper.deleteById(config.getId());
    }

    static FeatureConfigPo toPo(FeatureConfig c) {
        FeatureConfigPo po = new FeatureConfigPo();
        po.setId(c.getId());
        po.setConfigKey(c.getKey());
        po.setFeatureName(c.getName());
        po.setConfigGroup(c.getGroup());
        po.setEnabled(c.isEnabled() ? 1 : 0);
        po.setPublicFlag(c.isPublicFlag() ? 1 : 0);
        po.setBuiltIn(c.isBuiltIn() ? 1 : 0);
        po.setVersion(c.getVersion());
        po.setDescription(c.getDescription());
        po.setCreatedAt(c.getCreatedAt());
        po.setUpdatedAt(c.getUpdatedAt());
        return po;
    }

    static FeatureConfig toDomain(FeatureConfigPo po) {
        return new FeatureConfig(
                po.getId(), po.getConfigKey(), po.getFeatureName(), po.getConfigGroup(),
                po.getEnabled() != null && po.getEnabled() == 1,
                po.getPublicFlag() != null && po.getPublicFlag() == 1,
                po.getBuiltIn() != null && po.getBuiltIn() == 1,
                po.getVersion() == null ? 0 : po.getVersion(),
                po.getDescription(), po.getCreatedAt(), po.getUpdatedAt());
    }
}
