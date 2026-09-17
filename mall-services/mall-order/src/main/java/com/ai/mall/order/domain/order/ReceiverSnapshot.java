package com.ai.mall.order.domain.order;

/**
 * 收货地址快照值对象（CHG-0019）。
 *
 * <p>创建订单瞬间从 mall-member 复制落库；此后会员修改/删除地址不影响历史订单。
 *
 * @param receiverName    收货人
 * @param receiverPhone   手机号
 * @param province        省
 * @param city            市
 * @param district        区/县
 * @param detailAddress   详细地址
 * @param postalCode      邮编（可空）
 */
public record ReceiverSnapshot(String receiverName, String receiverPhone, String province, String city,
                               String district, String detailAddress, String postalCode) {
}
