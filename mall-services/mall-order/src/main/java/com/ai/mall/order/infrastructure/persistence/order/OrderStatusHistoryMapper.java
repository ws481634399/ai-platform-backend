package com.ai.mall.order.infrastructure.persistence.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** order_status_history Mapper（CHG-0019）。 */
@Mapper
public interface OrderStatusHistoryMapper extends BaseMapper<OrderStatusHistoryPo> {
}
