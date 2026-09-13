package com.ai.mall.product.domain.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("分类聚合自身规则")
class CategoryTest {

    @Test
    @DisplayName("名称 trim 后保存，空名/超长名拒绝")
    void nameNormalizedAndValidated() {
        Category category = Category.createNew("  服装  ", 0, 1, 0);
        assertThat(category.getName()).isEqualTo("服装");
        assertThat(category.getStatus().name()).isEqualTo("ENABLED");

        assertThatThrownBy(() -> Category.createNew("   ", 0, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Category.createNew("名".repeat(33), 0, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-008 禁用只是状态流转，不禁用子分类的语义由聚合无加载行为保证（启停互不影响）")
    void disableOnlyFlipsOwnStatus() {
        Category category = Category.createNew("鞋靴", 0, 1, 0);
        category.disable();
        assertThat(category.isEnabled()).isFalse();
        category.enable();
        assertThat(category.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("非法状态字符串重建聚合时拒绝")
    void illegalStatusRejected() {
        assertThatThrownBy(() -> Category.reconstitute(1L, "x", 0, 1, 0, "ARCHIVED", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
