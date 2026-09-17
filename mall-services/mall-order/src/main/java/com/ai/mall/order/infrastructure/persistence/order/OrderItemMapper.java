package com.ai.mall.order.infrastructure.persistence.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** order_item Mapper（CHG-0019）。 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemPo> {
}
