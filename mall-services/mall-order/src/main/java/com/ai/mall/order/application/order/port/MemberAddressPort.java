package com.ai.mall.order.application.order.port;

import java.util.Optional;

/**
 * mall-member 出站端口：按归属双条件读取收货地址（CHG-0019）。
 *
 * <p>mall-member 内部端点复用 {@code findByIdForMember(addressId, memberId)}；
 * 地址不存在或不属于该会员统一返回 empty（订单侧转 404，不泄露归属差异），
 * 传输/5xx 故障由实现归一为 503。
 */
public interface MemberAddressPort {

    Optional<AddressSnapshot> findOwnedAddress(long memberId, long addressId);

    /** 地址快照（含订单落库所需全部收货字段）。 */
    record AddressSnapshot(long addressId, long memberId, String receiverName, String receiverPhone,
                           String province, String city, String district, String detailAddress,
                           String postalCode, boolean defaultAddress) {
    }
}
