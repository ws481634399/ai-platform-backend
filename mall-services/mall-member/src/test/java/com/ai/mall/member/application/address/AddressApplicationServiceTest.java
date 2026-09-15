package com.ai.mall.member.application.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.member.domain.model.address.ShippingAddress;
import com.ai.mall.member.domain.repository.AddressRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

/**
 * 地址应用服务单元切片（CHG-0016 STORY-003-01-03-01）：
 * 上限/首条默认/404 归属语义，以及 TC-006 的 uk 冲突 → 409 转译（不依赖并发时序，确定性验证）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("STORY-003-01-03-01 AddressApplicationService")
class AddressApplicationServiceTest {

    private static final long MEMBER_ID = 81001L;

    @Mock AddressRepository repository;
    @InjectMocks AddressApplicationService service;

    private final AddressApplicationService.AddressFields fields =
            new AddressApplicationService.AddressFields("张三", "13800138000", "浙江省", "杭州市",
                    "西湖区", "文三路100号", "310000");

    @Test
    @DisplayName("首条地址（count=0）强制 isDefault=true 并回填后重查")
    void firstAddressForcedDefault() {
        when(repository.countByMember(MEMBER_ID)).thenReturn(0L);
        when(repository.insert(any(ShippingAddress.class))).thenAnswer(inv -> {
            ShippingAddress a = inv.getArgument(0);
            a.assignPersistedId(1L);
            return a;
        });
        when(repository.findByIdForMember(1L, MEMBER_ID)).thenReturn(Optional.of(
                ShippingAddress.reconstitute(1L, MEMBER_ID, "张三", "13800138000", "浙江省", "杭州市",
                        "西湖区", "文三路100号", "310000", true, Instant.now(), Instant.now())));

        ShippingAddress created = service.create(MEMBER_ID,
                new AddressApplicationService.AddressFields(" 张三 ", " 13800138000 ", "浙江省", "杭州市",
                        "西湖区", "文三路100号", " 310000 "));

        assertThat(created.isDefault()).isTrue();
        verify(repository).insert(any(ShippingAddress.class));
    }

    @Test
    @DisplayName("已有 20 条再新增 → 409 ADDRESS_LIMIT，不触达 insert")
    void limitRejected() {
        when(repository.countByMember(MEMBER_ID)).thenReturn(20L);

        assertThatThrownBy(() -> service.create(MEMBER_ID, fields))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(AddressErrorCode.ADDRESS_LIMIT);
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("第 2 条地址不强制默认")
    void secondAddressNotDefault() {
        when(repository.countByMember(MEMBER_ID)).thenReturn(1L);
        when(repository.insert(any(ShippingAddress.class))).thenAnswer(inv -> {
            ShippingAddress a = inv.getArgument(0);
            a.assignPersistedId(2L);
            return a;
        });
        when(repository.findByIdForMember(2L, MEMBER_ID)).thenReturn(Optional.of(
                ShippingAddress.reconstitute(2L, MEMBER_ID, "李四", "13900139000", "江苏省", "南京市",
                        "玄武区", "中山路1号", null, false, Instant.now(), Instant.now())));

        assertThat(service.create(MEMBER_ID, fields).isDefault()).isFalse();
    }

    @Test
    @DisplayName("update/delete/setDefault 零行归属命中 → 404 ADDRESS_NOT_FOUND")
    void ownership404() {
        when(repository.findByIdForMember(99L, MEMBER_ID)).thenReturn(Optional.empty());
        when(repository.deleteByIdForMember(99L, MEMBER_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.update(MEMBER_ID, 99L, fields))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(AddressErrorCode.ADDRESS_NOT_FOUND);
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        assertThatThrownBy(() -> service.delete(MEMBER_ID, 99L))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.setDefault(MEMBER_ID, 99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("TC-006 markDefault 撞生成列 uk（DuplicateKeyException）→ 409 DEFAULT_CONFLICT，且顺序为先清后设")
    void defaultConflictTranslated() {
        ShippingAddress existing = ShippingAddress.reconstitute(5L, MEMBER_ID, "张三", "13800138000",
                "浙江省", "杭州市", "西湖区", "文三路100号", null, false, Instant.now(), Instant.now());
        when(repository.findByIdForMember(5L, MEMBER_ID)).thenReturn(Optional.of(existing));
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_address_default"))
                .when(repository).markDefault(5L, MEMBER_ID);

        assertThatThrownBy(() -> service.setDefault(MEMBER_ID, 5L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(AddressErrorCode.ADDRESS_DEFAULT_CONFLICT);
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        InOrder order = inOrder(repository);
        order.verify(repository).findByIdForMember(5L, MEMBER_ID);
        order.verify(repository).clearDefault(MEMBER_ID);
        order.verify(repository).markDefault(eq(5L), eq(MEMBER_ID));
    }
}
