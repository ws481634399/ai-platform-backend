package com.ai.mall.product.domain.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("品牌聚合自身规则")
class BrandTest {

    @Test
    @DisplayName("名称 trim 后保存，默认启用，空名/超长名拒绝")
    void nameNormalizedAndValidated() {
        Brand brand = Brand.createNew("  Nike  ", null, null, 3);
        assertThat(brand.getName()).isEqualTo("Nike");
        assertThat(brand.isEnabled()).isTrue();
        assertThat(brand.getSort()).isEqualTo(3);

        assertThatThrownBy(() -> Brand.createNew("   ", null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Brand.createNew("名".repeat(65), null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("logo 为空归一为 null；非 http/https 或超长拒绝")
    void logoValidated() {
        assertThat(Brand.createNew("A", "  ", null, 0).getLogo()).isNull();
        assertThat(Brand.createNew("A", "https://cdn.example.com/a.png", null, 0).getLogo())
                .isEqualTo("https://cdn.example.com/a.png");

        assertThatThrownBy(() -> Brand.createNew("A", "ftp://example.com/a.png", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Brand.createNew("A", "not-a-url", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Brand.createNew("A", "https://example.com/" + "x".repeat(500), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("描述空白归一为 null，超长拒绝；负排序拒绝")
    void descriptionAndSortValidated() {
        assertThat(Brand.createNew("A", null, "  简介  ", 0).getDescription()).isEqualTo("简介");
        assertThatThrownBy(() -> Brand.createNew("A", null, "描".repeat(256), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Brand.createNew("A", null, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("资料更新与启停状态流转")
    void updateProfileAndStatus() {
        Brand brand = Brand.createNew("Old", null, null, 0);
        brand.updateProfile("New", "https://example.com/logo.png", "新描述", 9);
        assertThat(brand.getName()).isEqualTo("New");
        assertThat(brand.getLogo()).isEqualTo("https://example.com/logo.png");
        assertThat(brand.getSort()).isEqualTo(9);

        brand.disable();
        assertThat(brand.isEnabled()).isFalse();
        brand.enable();
        assertThat(brand.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("非法状态字符串重建聚合时拒绝")
    void illegalStatusRejected() {
        assertThatThrownBy(() -> Brand.reconstitute(1L, "x", null, null, 0, "ARCHIVED", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
