package com.ai.mall.inventory.infrastructure.persistence.inventory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper extends BaseMapper<InventoryPo> {

    @Update("UPDATE inventory_stock SET locked_quantity = locked_quantity + #{quantity}, version = version + 1 " +
            "WHERE sku_id = #{skuId} AND (total_quantity - locked_quantity) >= #{quantity}")
    int lockStock(@Param("skuId") long skuId, @Param("quantity") long quantity);
}
