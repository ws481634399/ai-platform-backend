package com.ai.mall.member.interfaces.rest.mall;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.AuthenticatedSubject;
import com.ai.mall.common.security.SecurityContextFacade;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.member.application.address.AddressApplicationService;
import com.ai.mall.member.application.address.AddressApplicationService.AddressFields;
import com.ai.mall.member.application.address.AddressApplicationService.AddressListResult;
import com.ai.mall.member.domain.model.address.ShippingAddress;
import com.ai.mall.member.interfaces.rest.mall.dto.ShippingAddressDtos.AddressListResponse;
import com.ai.mall.member.interfaces.rest.mall.dto.ShippingAddressDtos.AddressRequest;
import com.ai.mall.member.interfaces.rest.mall.dto.ShippingAddressDtos.AddressView;
import com.ai.mall.member.interfaces.rest.mall.dto.ShippingAddressDtos.DefaultAddressResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收货地址接口（CHG-0016 STORY-003-01-03-01）。
 *
 * <p>路径 SSOT：requirement-design §4 / story-design §2——{@code /api/mall/shipping-addresses}。
 * memberId 只从 {@link SecurityContextFacade} 取，任何接口不接受外部 memberId；
 * 类级 {@code ROLE_MEMBER} 收口，路径层安全链 {@code /api/mall/**} 双重保险。
 */
@RestController
@RequestMapping("/api/mall/shipping-addresses")
@PreAuthorize("hasRole('MEMBER')")
public class ShippingAddressController {

    private final AddressApplicationService addresses;

    public ShippingAddressController(AddressApplicationService addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    public UnifyResult<AddressListResponse> list() {
        long memberId = currentMemberId();
        AddressListResult result = addresses.list(memberId);
        List<AddressView> views = result.items().stream().map(ShippingAddressController::toView).toList();
        return UnifyResult.ok(new AddressListResponse(views, result.defaultId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnifyResult<AddressView> create(@Valid @RequestBody AddressRequest request) {
        ShippingAddress created = addresses.create(currentMemberId(), toFields(request));
        return UnifyResult.ok(toView(created));
    }

    @PutMapping("/{id}")
    public UnifyResult<AddressView> update(@PathVariable long id, @Valid @RequestBody AddressRequest request) {
        ShippingAddress updated = addresses.update(currentMemberId(), id, toFields(request));
        return UnifyResult.ok(toView(updated));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        addresses.delete(currentMemberId(), id);
    }

    @PutMapping("/{id}/default")
    public UnifyResult<AddressView> setDefault(@PathVariable long id) {
        ShippingAddress updated = addresses.setDefault(currentMemberId(), id);
        return UnifyResult.ok(toView(updated));
    }

    /** 字面路径 /default 声明在 /{id} 之外；GET 无单条端点，无路由歧义。 */
    @GetMapping("/default")
    public UnifyResult<DefaultAddressResponse> getDefault() {
        AddressView item = addresses.getDefault(currentMemberId())
                .map(ShippingAddressController::toView).orElse(null);
        return UnifyResult.ok(new DefaultAddressResponse(item));
    }

    /** 当前会话会员 ID：仅取自安全上下文（sub=Long.toString(memberId)），无入参来源。 */
    private static long currentMemberId() {
        AuthenticatedSubject subject = SecurityContextFacade.currentSubject()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        HttpStatus.UNAUTHORIZED, "请先登录"));
        try {
            return Long.parseLong(subject.subjectId());
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    HttpStatus.UNAUTHORIZED, "登录主体非法，请重新登录");
        }
    }

    private static AddressFields toFields(AddressRequest r) {
        return new AddressFields(r.receiverName(), r.receiverPhone(), r.province(), r.city(), r.district(),
                r.detailAddress(), r.postalCode());
    }

    private static AddressView toView(ShippingAddress a) {
        return new AddressView(a.id(), a.receiverName(), a.receiverPhone(), a.province(), a.city(),
                a.district(), a.detailAddress(), a.postalCode(), a.isDefault(), a.createdAt(), a.updatedAt());
    }
}
