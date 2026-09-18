package com.ai.mall.search.infrastructure.persistence.index;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SyncFailureMapper extends BaseMapper<SyncFailurePo> {
}
