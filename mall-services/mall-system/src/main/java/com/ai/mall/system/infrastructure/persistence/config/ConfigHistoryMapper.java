package com.ai.mall.system.infrastructure.persistence.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** system_config_history Mapper（CHG-0022）。 */
@Mapper
public interface ConfigHistoryMapper extends BaseMapper<ConfigHistoryPo> {
}
