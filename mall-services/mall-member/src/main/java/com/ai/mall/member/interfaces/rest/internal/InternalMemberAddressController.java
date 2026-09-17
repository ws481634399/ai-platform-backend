package com.ai.mall.member.interfaces.rest.internal;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.member.application.address.AddressErrorCode;
import com.ai.mall.member.domain.model.address.ShippingAddress;
import com.ai.mall.member.domain.repository.AddressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员收货地址内部查询接口（CHG-0019 REQ-M4-001）：order → member，X-Internal-Token 鉴权。
 *
 * <p>GET /api/internal/members/{memberId}/addresses/{addressId}：
 * 复用 {@code findByIdForMember} 归属双条件；不存在或不属于该会员统一 404（B0201），
 * 供订单侧预览/下单实时校验地址归属并复制快照。
 */
@RestController
@RequestMapping("/api/internal/members")
public class InternalMemberAddressController {

    private final AddressRepository addressRepository;

    public InternalMemberAddressController(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @GetMapping("/{memberId}/addresses/{addressId}")
    public UnifyResult<AddressView> findOwnedAddress(@PathVariable long memberId, @PathVariable long addressId) {
        ShippingAddress address = addressRepository.findByIdForMember(addressId, memberId)
                .orElseThrow(() -> new BusinessException(AddressErrorCode.ADDRESS_NOT_FOUND, HttpStatus.NOT_FOUND));
        return UnifyResult.ok(new AddressView(address.receiverName(), address.receiverPhone(), address.province(),
                address.city(), address.district(), address.detailAddress(), address.postalCode(),
                address.isDefault()));
    }

    /** 地址快照响应线框（id/会员 id 已在路径中，无需重复回传）。 */
    public record AddressView(String receiverName, String receiverPhone, String province, String city,
                              String district, String detailAddress, String postalCode, boolean defaultAddress) {
    }
}
