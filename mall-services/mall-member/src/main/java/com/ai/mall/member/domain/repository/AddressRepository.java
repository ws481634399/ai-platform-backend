package com.ai.mall.member.domain.repository;

import com.ai.mall.member.domain.model.address.ShippingAddress;
import java.util.List;
import java.util.Optional;

/**
 * 收货地址仓储端口（CHG-0016 STORY-003-01-03-01）。
 *
 * <p>归属收口：单条读/改/删一律 memberId+id 双条件，零行即「不存在或越权」，
 * 由应用层统一转 404（不区分两种情形）。
 */
public interface AddressRepository {

    /** 新建；自增主键回填到聚合。 */
    ShippingAddress insert(ShippingAddress address);

    /** 更新可变字段（不含默认标记）；仅按 memberId+id 更新，返回受影响行数。 */
    int update(ShippingAddress address);

    /** 删除；仅按 memberId+id 命中，返回受影响行数。 */
    int deleteByIdForMember(long id, long memberId);

    Optional<ShippingAddress> findByIdForMember(long id, long memberId);

    Optional<ShippingAddress> findDefault(long memberId);

    /** 列表：is_default DESC, updated_at DESC（story-design §1）。 */
    List<ShippingAddress> listByMember(long memberId);

    long countByMember(long memberId);

    /** 复位该会员当前默认地址（无则 0 行）。 */
    int clearDefault(long memberId);

    /** 将指定地址置默认；仅按 memberId+id 命中，返回受影响行数。 */
    int markDefault(long id, long memberId);
}
