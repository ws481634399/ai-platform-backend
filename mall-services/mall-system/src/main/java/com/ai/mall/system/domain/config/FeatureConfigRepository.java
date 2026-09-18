package com.ai.mall.system.domain.config;

import java.util.List;
import java.util.Optional;

/**
 * 功能开关仓储端口（CHG-0022）。
 */
public interface FeatureConfigRepository {

    Optional<FeatureConfig> findByKey(String key);

    /** 分页：group/enabled 可空（不过滤），按 id 升序稳定排序。 */
    List<FeatureConfig> findPage(String group, Boolean enabled, long offset, int limit);

    long count(String group, Boolean enabled);

    /** 全部公开开关（publicFlag=1，含禁用项），按 id 升序。 */
    List<FeatureConfig> findAllPublic();

    void insert(FeatureConfig config);

    /**
     * 乐观锁更新：modified.version() 已为旧版本+1，WHERE 匹配旧版本。
     *
     * @return 是否更新到行（false 表示版本冲突）
     */
    boolean casUpdate(FeatureConfig modified, int expectedVersion);

    void delete(FeatureConfig config);
}
