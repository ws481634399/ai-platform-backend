package com.ai.mall.system.infrastructure.persistence.config;

import com.ai.mall.system.domain.config.ConfigType;
import com.ai.mall.system.domain.config.EffectType;
import com.ai.mall.system.domain.config.SystemParameter;
import com.ai.mall.system.domain.config.SystemParameterRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 系统参数仓储 MyBatis-Plus 实现（CHG-0022）。 */
@Repository
public class MyBatisSystemParameterRepository implements SystemParameterRepository {

    private final SystemParameterMapper mapper;

    public MyBatisSystemParameterRepository(SystemParameterMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<SystemParameter> findByKey(String key) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<SystemParameterPo>()
                .eq(SystemParameterPo::getConfigKey, key)))
                .map(MyBatisSystemParameterRepository::toDomain);
    }

    @Override
    public List<SystemParameter> findPage(String group, String type, long offset, int limit) {
        LambdaQueryWrapper<SystemParameterPo> wrapper = new LambdaQueryWrapper<SystemParameterPo>()
                .orderByAsc(SystemParameterPo::getId)
                .last("LIMIT " + Math.max(0, offset) + "," + Math.max(1, limit));
        if (group != null && !group.isBlank()) {
            wrapper.eq(SystemParameterPo::getConfigGroup, group);
        }
        if (type != null && !type.isBlank()) {
            wrapper.eq(SystemParameterPo::getParameterType, type);
        }
        return mapper.selectList(wrapper).stream()
                .map(MyBatisSystemParameterRepository::toDomain).toList();
    }

    @Override
    public long count(String group, String type) {
        LambdaQueryWrapper<SystemParameterPo> wrapper = new LambdaQueryWrapper<>();
        if (group != null && !group.isBlank()) {
            wrapper.eq(SystemParameterPo::getConfigGroup, group);
        }
        if (type != null && !type.isBlank()) {
            wrapper.eq(SystemParameterPo::getParameterType, type);
        }
        return mapper.selectCount(wrapper);
    }

    @Override
    public void insert(SystemParameter parameter) {
        SystemParameterPo po = toPo(parameter);
        mapper.insert(po);
        parameter.assignId(po.getId());
    }

    @Override
    public boolean casUpdate(SystemParameter modified, int expectedVersion) {
        return mapper.update(null, new LambdaUpdateWrapper<SystemParameterPo>()
                .eq(SystemParameterPo::getId, modified.getId())
                .eq(SystemParameterPo::getVersion, expectedVersion)
                .set(SystemParameterPo::getParameterName, modified.getName())
                .set(SystemParameterPo::getConfigGroup, modified.getGroup())
                .set(SystemParameterPo::getConfigValue, modified.getValue())
                .set(SystemParameterPo::getDefaultValue, modified.getDefaultValue())
                .set(SystemParameterPo::getMinValue, modified.getMinValue())
                .set(SystemParameterPo::getMaxValue, modified.getMaxValue())
                .set(SystemParameterPo::getPublicFlag, modified.isPublicFlag() ? 1 : 0)
                .set(SystemParameterPo::getDescription, modified.getDescription())
                .set(SystemParameterPo::getVersion, modified.getVersion())
                .set(SystemParameterPo::getUpdatedAt, modified.getUpdatedAt())) > 0;
    }

    @Override
    public void delete(SystemParameter parameter) {
        mapper.deleteById(parameter.getId());
    }

    static SystemParameterPo toPo(SystemParameter p) {
        SystemParameterPo po = new SystemParameterPo();
        po.setId(p.getId());
        po.setConfigKey(p.getKey());
        po.setParameterName(p.getName());
        po.setConfigGroup(p.getGroup());
        po.setParameterType(p.getType().name());
        po.setConfigValue(p.getValue());
        po.setDefaultValue(p.getDefaultValue());
        po.setMinValue(p.getMinValue());
        po.setMaxValue(p.getMaxValue());
        po.setEffectType(p.getEffectType().name());
        po.setPublicFlag(p.isPublicFlag() ? 1 : 0);
        po.setBuiltIn(p.isBuiltIn() ? 1 : 0);
        po.setVersion(p.getVersion());
        po.setDescription(p.getDescription());
        po.setCreatedAt(p.getCreatedAt());
        po.setUpdatedAt(p.getUpdatedAt());
        return po;
    }

    static SystemParameter toDomain(SystemParameterPo po) {
        return new SystemParameter(
                po.getId(), po.getConfigKey(), po.getParameterName(), po.getConfigGroup(),
                ConfigType.valueOf(po.getParameterType()),
                po.getConfigValue(), po.getDefaultValue(), po.getMinValue(), po.getMaxValue(),
                EffectType.valueOf(po.getEffectType() == null ? "DYNAMIC" : po.getEffectType()),
                po.getPublicFlag() != null && po.getPublicFlag() == 1,
                po.getBuiltIn() != null && po.getBuiltIn() == 1,
                po.getVersion() == null ? 0 : po.getVersion(),
                po.getDescription(), po.getCreatedAt(), po.getUpdatedAt());
    }
}
