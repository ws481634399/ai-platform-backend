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

    /**
     * CHG-0019 释放预留（CAS 条件更新）：locked 扣减前必须仍 >= 预留量，
     * 防止与并发确认/调整交错导致 locked 被扣成负数。
     */
    @Update("UPDATE inventory_stock SET locked_quantity = locked_quantity - #{quantity}, version = version + 1 " +
            "WHERE sku_id = #{skuId} AND locked_quantity >= #{quantity}")
    int releaseStock(@Param("skuId") long skuId, @Param("quantity") long quantity);

    /**
     * CHG-0019 确认扣减（CAS 条件更新）：total/locked 同减，仍以 locked >= 预留量为条件。
     */
    @Update("UPDATE inventory_stock SET total_quantity = total_quantity - #{quantity}, " +
            "locked_quantity = locked_quantity - #{quantity}, version = version + 1 " +
            "WHERE sku_id = #{skuId} AND locked_quantity >= #{quantity}")
    int deductStock(@Param("skuId") long skuId, @Param("quantity") long quantity);
}
