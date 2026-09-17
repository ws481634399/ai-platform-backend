package com.ai.mall.order.infrastructure.persistence.compensation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** compensation_task Mapper（CHG-0019 REQ-M4-004）。 */
@Mapper
public interface CompensationTaskMapper extends BaseMapper<CompensationTaskPo> {
}
