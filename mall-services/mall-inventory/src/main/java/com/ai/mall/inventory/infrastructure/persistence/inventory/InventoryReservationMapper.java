package com.ai.mall.inventory.infrastructure.persistence.inventory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryReservationMapper extends BaseMapper<InventoryReservationPo> {

    /**
     * CHG-0025 M7：按订单号枚举该单全部 per-line 预留。
     * per-line reservationId 格式 = orderNo:skuId，前缀匹配一次查全。
     */
    @Select("SELECT reservation_id FROM inventory_reservation WHERE reservation_id LIKE #{prefix}")
    List<String> findReservationIdsLike(@Param("prefix") String prefix);

    /**
     * CHG-0019 预留状态 CAS：仅当当前状态等于 fromStatus 时迁移到 toStatus。
     * 同一预留的 release/confirm 并发由此串行仲裁，配合库存条件更新在同事务回滚，
     * 保证重复/并发请求只产生一次数量变化。
     */
    @Update("UPDATE inventory_reservation SET status = #{toStatus}, updated_at = #{now} " +
            "WHERE id = #{id} AND status = #{fromStatus}")
    int casStatus(@Param("id") long id, @Param("fromStatus") String fromStatus,
                  @Param("toStatus") String toStatus, @Param("now") Instant now);
}
