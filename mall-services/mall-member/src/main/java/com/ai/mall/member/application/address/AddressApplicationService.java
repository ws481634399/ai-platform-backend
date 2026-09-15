package com.ai.mall.member.application.address;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.member.domain.model.address.ShippingAddress;
import com.ai.mall.member.domain.repository.AddressRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收货地址应用服务（CHG-0016 STORY-003-01-03-01）。
 *
 * <p>职责：CRUD / 设默认 / 默认查询；memberId 全部由接口层从 SecurityContext 传入，
 * 本服务不接受任何外部 ID。规则：
 * <ul>
 *   <li>上限 20：新增前计数，第 21 条 409 ADDRESS_LIMIT；</li>
 *   <li>首条地址强制默认；</li>
 *   <li>setDefault 单事务清旧设新，并发由生成列 uk 兜底为 409（回滚无半态）；</li>
 *   <li>删除默认不自动重选（getDefault 返空）；</li>
 *   <li>update/delete/get 零行统一 404 ADDRESS_NOT_FOUND（不区分不存在/越权）。</li>
 * </ul>
 */
@Service
public class AddressApplicationService {

    private final AddressRepository addresses;

    public AddressApplicationService(AddressRepository addresses) {
        this.addresses = addresses;
    }

    /** 列表（默认优先、再按更新时间）+ 当前默认 ID（无默认为 null）。 */
    @Transactional(readOnly = true)
    public AddressListResult list(long memberId) {
        List<ShippingAddress> items = addresses.listByMember(memberId);
        Long defaultId = items.stream().filter(ShippingAddress::isDefault).map(ShippingAddress::id)
                .findFirst().orElse(null);
        return new AddressListResult(items, defaultId);
    }

    /** 新增：20 上限；首条强制默认。 */
    @Transactional
    public ShippingAddress create(long memberId, AddressFields fields) {
        long count = addresses.countByMember(memberId);
        if (count >= ShippingAddress.MEMBER_ADDRESS_LIMIT) {
            throw new BusinessException(AddressErrorCode.ADDRESS_LIMIT, HttpStatus.CONFLICT);
        }
        AddressFields normalized = normalize(fields);
        ShippingAddress address = ShippingAddress.create(memberId, normalized.receiverName(),
                normalized.receiverPhone(), normalized.province(), normalized.city(), normalized.district(),
                normalized.detailAddress(), normalized.postalCode(), count == 0, Instant.now());
        addresses.insert(address);
        return reload(address.id(), memberId);
    }

    /** 修改内容（不改默认标记）；零行 404。 */
    @Transactional
    public ShippingAddress update(long memberId, long addressId, AddressFields fields) {
        ShippingAddress address = loadOwned(addressId, memberId);
        AddressFields normalized = normalize(fields);
        address.revise(normalized.receiverName(), normalized.receiverPhone(), normalized.province(),
                normalized.city(), normalized.district(), normalized.detailAddress(), normalized.postalCode());
        addresses.update(address);
        return reload(addressId, memberId);
    }

    /** 删除；零行 404。删除默认后不自动重选默认。 */
    @Transactional
    public void delete(long memberId, long addressId) {
        int affected = addresses.deleteByIdForMember(addressId, memberId);
        if (affected == 0) {
            throw notFound();
        }
    }

    /**
     * 设默认：同事务先清本会员旧默认再置新；撞 default_member_flag 唯一键
     * （另一事务刚提交）→ 409，事务回滚，最终仍仅一个默认。
     */
    @Transactional
    public ShippingAddress setDefault(long memberId, long addressId) {
        loadOwned(addressId, memberId);
        addresses.clearDefault(memberId);
        try {
            addresses.markDefault(addressId, memberId);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(AddressErrorCode.ADDRESS_DEFAULT_CONFLICT, HttpStatus.CONFLICT);
        }
        return reload(addressId, memberId);
    }

    /** 默认地址；无则 empty（接口层包 {item:null} 200）。 */
    @Transactional(readOnly = true)
    public Optional<ShippingAddress> getDefault(long memberId) {
        return addresses.findDefault(memberId);
    }

    private ShippingAddress loadOwned(long addressId, long memberId) {
        return addresses.findByIdForMember(addressId, memberId).orElseThrow(AddressApplicationService::notFound);
    }

    private ShippingAddress reload(long addressId, long memberId) {
        return addresses.findByIdForMember(addressId, memberId)
                .orElseThrow(() -> new IllegalStateException("address vanished after write: " + addressId));
    }

    /** 去首尾空白；postalCode 空串归一 null。 */
    private static AddressFields normalize(AddressFields f) {
        String postal = f.postalCode() == null ? null : f.postalCode().trim();
        return new AddressFields(
                f.receiverName().trim(),
                f.receiverPhone().trim(),
                f.province().trim(),
                f.city().trim(),
                f.district().trim(),
                f.detailAddress().trim(),
                postal == null || postal.isEmpty() ? null : postal);
    }

    private static BusinessException notFound() {
        return new BusinessException(AddressErrorCode.ADDRESS_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    /** 地址内容字段（新增/修改共用）；各值为原始入参，服务内 trim/归一。 */
    public record AddressFields(String receiverName, String receiverPhone, String province, String city,
                                String district, String detailAddress, String postalCode) {
    }

    /** 列表结果：items 已按默认优先/更新时间排序；defaultId 无默认时为 null。 */
    public record AddressListResult(List<ShippingAddress> items, Long defaultId) {
    }
}
