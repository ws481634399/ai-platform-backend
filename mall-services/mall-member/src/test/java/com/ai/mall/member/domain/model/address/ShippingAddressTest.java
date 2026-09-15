package com.ai.mall.member.domain.model.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 收货地址聚合不变量（CHG-0016 STORY-003-01-03-01，TC-008 单元切片）。
 */
@DisplayName("STORY-003-01-03-01 ShippingAddress 聚合校验")
class ShippingAddressTest {

    private static final Instant NOW = Instant.parse("2026-09-20T08:00:00Z");

    private ShippingAddress validDefault() {
        return ShippingAddress.create(8001L, "张三", "13800138000", "浙江省", "杭州市", "西湖区",
                "文三路100号", "310000", true, NOW);
    }

    @Test
    @DisplayName("合法地址创建成功；首条默认标记与字段原样保留；postalCode 可空")
    void validCreate() {
        ShippingAddress withoutPostal = ShippingAddress.create(8001L, "张三", "13800138000",
                "浙江省", "杭州市", "西湖区", "文三路100号", null, false, NOW);
        assertThat(withoutPostal.postalCode()).isNull();
        assertThat(withoutPostal.isDefault()).isFalse();

        ShippingAddress withPostal = validDefault();
        assertThat(withPostal.id()).isNull();
        assertThat(withPostal.memberId()).isEqualTo(8001L);
        assertThat(withPostal.receiverName()).isEqualTo("张三");
        assertThat(withPostal.isDefault()).isTrue();
        assertThat(withPostal.createdAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("收货人姓名空白 → IllegalArgumentException")
    void blankReceiverName(String name) {
        assertThatThrownBy(() -> ShippingAddress.create(8001L, name, "13800138000",
                "浙江省", "杭州市", "西湖区", "文三路100号", null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("收货人姓名");
    }

    @Test
    @DisplayName("收货人姓名 33 字超长 → IllegalArgumentException；边界 32 合法")
    void receiverNameLengthBoundary() {
        assertThat(ShippingAddress.create(8001L, "张".repeat(32), "13800138000",
                "浙江省", "杭州市", "西湖区", "文三路100号", null, false, NOW).receiverName())
                .hasSize(32);
        assertThatThrownBy(() -> ShippingAddress.create(8001L, "张".repeat(33), "13800138000",
                "浙江省", "杭州市", "西湖区", "文三路100号", null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("收货人姓名");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345", "1380013800", "23800000000", "1380013800a"})
    @DisplayName("手机号非法 → IllegalArgumentException")
    void invalidPhone(String phone) {
        assertThatThrownBy(() -> ShippingAddress.create(8001L, "张三", phone,
                "浙江省", "杭州市", "西湖区", "文三路100号", null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("手机号");
    }

    @Test
    @DisplayName("省/市/区空白或超长（65 字）→ IllegalArgumentException")
    void invalidRegions() {
        String tooLongRegion = "省".repeat(65);
        assertThatThrownBy(() -> ShippingAddress.create(8001L, "张三", "13800138000",
                tooLongRegion, "杭州市", "西湖区", "文三路100号", null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("省份");
        assertThatThrownBy(() -> ShippingAddress.create(8001L, "张三", "13800138000",
                "浙江省", " ", "西湖区", "文三路100号", null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("城市");
        assertThatThrownBy(() -> ShippingAddress.create(8001L, "张三", "13800138000",
                "浙江省", "杭州市", "", "文三路100号", null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("区县");
    }

    @Test
    @DisplayName("详细地址空白或超长（129 字）→ IllegalArgumentException；边界 128 合法")
    void detailLengthBoundary() {
        String boundary = "号".repeat(128);
        assertThat(ShippingAddress.create(8001L, "张三", "13800138000", "浙江省", "杭州市", "西湖区",
                boundary, null, false, NOW).detailAddress()).hasSize(128);

        assertThatThrownBy(() -> ShippingAddress.create(8001L, "张三", "13800138000",
                "浙江省", "杭州市", "西湖区", "详".repeat(129), null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("详细地址");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567", "abcdef"})
    @DisplayName("邮政编码非 6 位数字 → IllegalArgumentException；空串与 null 需由应用层归一为 null")
    void invalidPostalCode(String postal) {
        assertThatThrownBy(() -> validDefault().revise("张三", "13800138000", "浙江省", "杭州市",
                "西湖区", "文三路100号", postal))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("邮政编码");
    }

    @Test
    @DisplayName("revise 合法修改并刷新 updatedAt；非法修改不落任何字段（异常前不赋值）")
    void reviseValidates() {
        ShippingAddress address = validDefault();
        Instant revisedAtFloor = Instant.now();
        address.revise("李四", "13900139000", "江苏省", "南京市", "玄武区", "中山路1号", "210000");
        assertThat(address.receiverName()).isEqualTo("李四");
        assertThat(address.postalCode()).isEqualTo("210000");
        assertThat(address.updatedAt()).isAfterOrEqualTo(revisedAtFloor);

        assertThatThrownBy(() -> address.revise("王五", "bad", "江苏省", "南京市",
                "玄武区", "中山路1号", "210000"))
                .isInstanceOf(IllegalArgumentException.class);
        // 异常发生在赋值前：姓名仍为李四
        assertThat(address.receiverName()).isEqualTo("李四");
        assertThat(address.receiverPhone()).isEqualTo("13900139000");
    }

    @Test
    @DisplayName("memberId 非法或重复回填主键 → IllegalStateException/IllegalArgumentException")
    void badMemberIdAndDoubleAssign() {
        assertThatThrownBy(() -> ShippingAddress.create(0L, "张三", "13800138000",
                "浙江省", "杭州市", "西湖区", "文三路100号", null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        ShippingAddress address = validDefault();
        address.assignPersistedId(99L);
        assertThatThrownBy(() -> address.assignPersistedId(100L))
                .isInstanceOf(IllegalStateException.class);
    }
}
