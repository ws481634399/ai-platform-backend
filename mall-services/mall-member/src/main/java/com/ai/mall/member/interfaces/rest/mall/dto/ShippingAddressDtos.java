package com.ai.mall.member.interfaces.rest.mall.dto;

import com.ai.mall.common.web.annotation.StringId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * 收货地址接口 DTO（CHG-0016 STORY-003-01-03-01）。
 *
 * <p>契约 SSOT：story-design §2。memberId 永不出现在请求/响应中；出参 id 一律字符串。
 */
public final class ShippingAddressDtos {

    private ShippingAddressDtos() {
    }

    /** 地址视图 AddressView（story-design §2，无 memberId）。 */
    public record AddressView(
            @StringId long id,
            String receiverName,
            String receiverPhone,
            String province,
            String city,
            String district,
            String detailAddress,
            String postalCode,
            boolean isDefault,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** 新增/修改请求体（postalCode 可空；@Pattern 对 null 放行）。 */
    public record AddressRequest(
            @NotBlank(message = "收货人姓名不能为空")
            @Size(max = 32, message = "收货人姓名长度不能超过32字")
            String receiverName,
            @NotBlank(message = "收货人手机号不能为空")
            @Pattern(regexp = "^1[3-9]\\d{9}$", message = "收货人手机号格式不正确")
            String receiverPhone,
            @NotBlank(message = "省份不能为空")
            @Size(max = 64, message = "省份长度不能超过64字")
            String province,
            @NotBlank(message = "城市不能为空")
            @Size(max = 64, message = "城市长度不能超过64字")
            String city,
            @NotBlank(message = "区县不能为空")
            @Size(max = 64, message = "区县长度不能超过64字")
            String district,
            @NotBlank(message = "详细地址不能为空")
            @Size(max = 128, message = "详细地址长度不能超过128字")
            String detailAddress,
            @Pattern(regexp = "^\\d{6}$", message = "邮政编码必须为6位数字")
            String postalCode) {
    }

    /** GET 列表响应：items 默认优先排序；defaultId 无默认时为 null。 */
    public record AddressListResponse(List<AddressView> items, @StringId Long defaultId) {
    }

    /** GET /default 响应：无默认时 item=null，HTTP 仍 200。 */
    public record DefaultAddressResponse(AddressView item) {
    }
}
