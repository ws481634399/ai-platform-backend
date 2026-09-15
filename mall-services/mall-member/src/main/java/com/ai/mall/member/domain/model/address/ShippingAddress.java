package com.ai.mall.member.domain.model.address;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 收货地址聚合根（CHG-0016 STORY-003-01-03-01）。
 *
 * <p>归属不可变（memberId 仅创建时给定，仓储所有读写双条件 memberId+id）；
 * 默认标记不在聚合内维护互斥——由应用服务在单事务内清旧设新、
 * shipping_address.default_member_flag 生成列唯一索引兜底并发。
 * 全部字段不变量在聚合内收口，拒绝非法状态落库。
 */
public final class ShippingAddress {

    /** 收货人姓名长度 1–32（story-spec §3）。 */
    public static final int RECEIVER_NAME_MAX_LENGTH = 32;

    /** 省/市/区长度上限 1–64。 */
    public static final int REGION_MAX_LENGTH = 64;

    /** 详细地址长度 1–128。 */
    public static final int DETAIL_MAX_LENGTH = 128;

    public static final int PHONE_MAX_LENGTH = 20;
    public static final int POSTAL_CODE_LENGTH = 6;

    /** 每会员地址数量上限（story-spec §3 / AC-024）。 */
    public static final int MEMBER_ADDRESS_LIMIT = 20;

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern POSTAL_CODE = Pattern.compile("^\\d{6}$");

    /** 持久化后回填；新建未落库时为 null。 */
    private Long id;
    private final long memberId;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private String postalCode;
    private boolean defaultAddress;
    private final Instant createdAt;
    private Instant updatedAt;

    private ShippingAddress(Long id, long memberId, String receiverName, String receiverPhone,
                            String province, String city, String district, String detailAddress,
                            String postalCode, boolean defaultAddress, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.memberId = memberId;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.province = province;
        this.city = city;
        this.district = district;
        this.detailAddress = detailAddress;
        this.postalCode = postalCode;
        this.defaultAddress = defaultAddress;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 新建地址。
     *
     * @param isDefault 是否强制为默认（应用层按「该会员首条地址」判定）
     * @throws IllegalArgumentException 任一字段不合法
     */
    public static ShippingAddress create(long memberId, String receiverName, String receiverPhone,
                                         String province, String city, String district, String detailAddress,
                                         String postalCode, boolean isDefault, Instant now) {
        if (memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        validate(receiverName, receiverPhone, province, city, district, detailAddress, postalCode);
        return new ShippingAddress(null, memberId, receiverName, receiverPhone, province, city, district,
                detailAddress, postalCode, isDefault, now, now);
    }

    /** 持久化重建（时间戳以数据库 NOW(6) 为准）。 */
    public static ShippingAddress reconstitute(long id, long memberId, String receiverName, String receiverPhone,
                                               String province, String city, String district, String detailAddress,
                                               String postalCode, boolean isDefault, Instant createdAt,
                                               Instant updatedAt) {
        return new ShippingAddress(id, memberId, receiverName, receiverPhone, province, city, district,
                detailAddress, postalCode, isDefault, createdAt, updatedAt);
    }

    /**
     * 修改可变内容（不含默认标记，默认互斥由 setDefault 专径处理）。
     *
     * @throws IllegalArgumentException 任一字段不合法
     */
    public void revise(String receiverName, String receiverPhone, String province, String city,
                       String district, String detailAddress, String postalCode) {
        validate(receiverName, receiverPhone, province, city, district, detailAddress, postalCode);
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.province = province;
        this.city = city;
        this.district = district;
        this.detailAddress = detailAddress;
        this.postalCode = postalCode;
        this.updatedAt = Instant.now();
    }

    /** 持久化回填自增主键（仅基础设施仓储在 insert 成功后调用）。 */
    public void assignPersistedId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("address already persisted: " + this.id);
        }
        this.id = id;
    }

    private static void validate(String receiverName, String receiverPhone, String province, String city,
                                 String district, String detailAddress, String postalCode) {
        requireTrimmed(receiverName, RECEIVER_NAME_MAX_LENGTH, "收货人姓名");
        if (receiverPhone == null || receiverPhone.length() > PHONE_MAX_LENGTH
                || !PHONE.matcher(receiverPhone).matches()) {
            throw new IllegalArgumentException("收货人手机号格式不正确");
        }
        requireTrimmed(province, REGION_MAX_LENGTH, "省份");
        requireTrimmed(city, REGION_MAX_LENGTH, "城市");
        requireTrimmed(district, REGION_MAX_LENGTH, "区县");
        requireTrimmed(detailAddress, DETAIL_MAX_LENGTH, "详细地址");
        if (postalCode != null && !POSTAL_CODE.matcher(postalCode).matches()) {
            throw new IllegalArgumentException("邮政编码必须为6位数字");
        }
    }

    private static void requireTrimmed(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + "长度不能超过" + maxLength + "字");
        }
    }

    public Long id() {
        return id;
    }

    public long memberId() {
        return memberId;
    }

    public String receiverName() {
        return receiverName;
    }

    public String receiverPhone() {
        return receiverPhone;
    }

    public String province() {
        return province;
    }

    public String city() {
        return city;
    }

    public String district() {
        return district;
    }

    public String detailAddress() {
        return detailAddress;
    }

    public String postalCode() {
        return postalCode;
    }

    public boolean isDefault() {
        return defaultAddress;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
