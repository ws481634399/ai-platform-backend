package com.ai.mall.inventory.infrastructure.persistence.inventory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryLogMapper extends BaseMapper<InventoryLogPo> {
}
