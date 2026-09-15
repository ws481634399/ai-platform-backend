package com.ai.mall.member.infrastructure.persistence.address;

import com.ai.mall.member.domain.model.address.ShippingAddress;
import com.ai.mall.member.domain.repository.AddressRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 收货地址仓储 MyBatis 实现（CHG-0016 STORY-003-01-03-01）。
 */
@Repository
public class MyBatisAddressRepository implements AddressRepository {

    private final AddressMapper mapper;

    public MyBatisAddressRepository(AddressMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ShippingAddress insert(ShippingAddress address) {
        AddressPo po = toPo(address);
        mapper.insert(po);
        address.assignPersistedId(po.getId());
        return address;
    }

    @Override
    public int update(ShippingAddress address) {
        return mapper.update(toPo(address));
    }

    @Override
    public int deleteByIdForMember(long id, long memberId) {
        return mapper.deleteByIdForMember(id, memberId);
    }

    @Override
    public Optional<ShippingAddress> findByIdForMember(long id, long memberId) {
        return Optional.ofNullable(mapper.findByIdForMember(id, memberId)).map(MyBatisAddressRepository::toDomain);
    }

    @Override
    public Optional<ShippingAddress> findDefault(long memberId) {
        return Optional.ofNullable(mapper.findDefault(memberId)).map(MyBatisAddressRepository::toDomain);
    }

    @Override
    public List<ShippingAddress> listByMember(long memberId) {
        return mapper.listByMember(memberId).stream().map(MyBatisAddressRepository::toDomain).toList();
    }

    @Override
    public long countByMember(long memberId) {
        return mapper.countByMember(memberId);
    }

    @Override
    public int clearDefault(long memberId) {
        return mapper.clearDefault(memberId);
    }

    @Override
    public int markDefault(long id, long memberId) {
        return mapper.markDefault(id, memberId);
    }

    private static AddressPo toPo(ShippingAddress a) {
        AddressPo po = new AddressPo();
        if (a.id() != null) {
            po.setId(a.id());
        }
        po.setMemberId(a.memberId());
        po.setReceiverName(a.receiverName());
        po.setReceiverPhone(a.receiverPhone());
        po.setProvince(a.province());
        po.setCity(a.city());
        po.setDistrict(a.district());
        po.setDetailAddress(a.detailAddress());
        po.setPostalCode(a.postalCode());
        po.setDefaultAddress(a.isDefault());
        po.setCreatedAt(a.createdAt());
        po.setUpdatedAt(a.updatedAt());
        return po;
    }

    private static ShippingAddress toDomain(AddressPo po) {
        Instant created = po.getCreatedAt() == null ? Instant.EPOCH : po.getCreatedAt();
        Instant updated = po.getUpdatedAt() == null ? created : po.getUpdatedAt();
        return ShippingAddress.reconstitute(po.getId(), po.getMemberId(), po.getReceiverName(),
                po.getReceiverPhone(), po.getProvince(), po.getCity(), po.getDistrict(), po.getDetailAddress(),
                po.getPostalCode(), po.isDefaultAddress(), created, updated);
    }
}
