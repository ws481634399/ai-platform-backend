package com.ai.mall.order.infrastructure.persistence.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** orders Mapper：基础 CRUD + 动作专属 CAS 条件更新（CHG-0019）。 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderPo> {

    /** 支付：仅 PENDING_PAYMENT + 指定 version 命中，迁移 PAID 并自增 version。 */
    @Update("UPDATE orders SET status = 'PAID', paid_at = #{now}, updated_at = #{now}, version = version + 1 "
            + "WHERE id = #{id} AND status = 'PENDING_PAYMENT' AND version = #{version}")
    int casPay(@Param("id") long id, @Param("version") long version, @Param("now") Instant now);

    /** 会员取消：仅 PENDING_PAYMENT + 指定 version 命中。 */
    @Update("UPDATE orders SET status = 'CANCELLED', cancel_reason = #{reason}, cancelled_at = #{now}, "
            + "updated_at = #{now}, version = version + 1 "
            + "WHERE id = #{id} AND status = 'PENDING_PAYMENT' AND version = #{version}")
    int casCancel(@Param("id") long id, @Param("version") long version, @Param("reason") String reason,
                  @Param("now") Instant now);

    /** 后台发货：仅 PAID + 指定 version 命中。 */
    @Update("UPDATE orders SET status = 'SHIPPED', delivery_company = #{deliveryCompany}, tracking_no = #{trackingNo}, "
            + "shipped_at = #{now}, updated_at = #{now}, version = version + 1 "
            + "WHERE id = #{id} AND status = 'PAID' AND version = #{version}")
    int casShip(@Param("id") long id, @Param("version") long version,
                @Param("deliveryCompany") String deliveryCompany, @Param("trackingNo") String trackingNo,
                @Param("now") Instant now);

    /** 会员确认收货：仅 SHIPPED + 指定 version 命中。 */
    @Update("UPDATE orders SET status = 'COMPLETED', completed_at = #{now}, updated_at = #{now}, "
            + "version = version + 1 WHERE id = #{id} AND status = 'SHIPPED' AND version = #{version}")
    int casComplete(@Param("id") long id, @Param("version") long version, @Param("now") Instant now);
}
