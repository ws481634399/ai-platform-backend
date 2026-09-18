package com.ai.mall.system.domain.config;

import java.util.List;
import java.util.Optional;

/**
 * 系统参数仓储端口（CHG-0022）。
 */
public interface SystemParameterRepository {

    Optional<SystemParameter> findByKey(String key);

    List<SystemParameter> findPage(String group, String type, long offset, int limit);

    long count(String group, String type);

    void insert(SystemParameter parameter);

    /**
     * 乐观锁更新：modified.version() 已为旧版本+1，WHERE 匹配旧版本。
     *
     * @return 是否更新到行（false 表示版本冲突）
     */
    boolean casUpdate(SystemParameter modified, int expectedVersion);

    void delete(SystemParameter parameter);
}
