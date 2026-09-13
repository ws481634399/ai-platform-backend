package com.ai.mall.product.domain.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.mall.product.domain.category.CategoryRules.Node;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分类跨实体不变量纯领域单测（TC-002/003/004/006/012 的规则层覆盖）。
 */
@DisplayName("分类层级不变量")
class CategoryRulesTest {

    private static Node node(long id, long parentId, int level, boolean enabled) {
        return new Node(id, parentId, level, enabled);
    }

    @Test
    @DisplayName("TC-002/TC-012 一级父级为空时层级为 1，逐级递增到 3，第 4 级拒绝")
    void createLevelProgression() {
        assertThat(CategoryRules.levelForCreate(null)).isEqualTo(1);
        assertThat(CategoryRules.levelForCreate(node(1, 0, 1, true))).isEqualTo(2);
        assertThat(CategoryRules.levelForCreate(node(2, 1, 2, true))).isEqualTo(3);
        assertThatThrownBy(() -> CategoryRules.levelForCreate(node(3, 2, 3, true)))
                .isInstanceOf(CategoryException.class)
                .hasMessageContaining("3 级");
    }

    @Test
    @DisplayName("TC-006 禁用父级下新增子分类被拒")
    void disabledParentRejected() {
        assertThatThrownBy(() -> CategoryRules.levelForCreate(node(1, 0, 1, false)))
                .isInstanceOf(CategoryException.class);
    }

    @Test
    @DisplayName("TC-003 新父是自身时拒绝")
    void selfReferenceRejected() {
        Node self = node(10, 0, 1, true);
        assertThatThrownBy(() -> CategoryRules.validateMove(self, self, List.of(), List.of(self)))
                .isInstanceOf(CategoryException.class);
    }

    @Test
    @DisplayName("TC-004 A→B→C 链下把祖先 A 挂到后代 C 下形成循环，拒绝")
    void cycleRejected() {
        Node a = node(1, 0, 1, true);
        Node b = node(2, 1, 2, true);
        Node c = node(3, 2, 3, true);
        // 把 A 移到 C 下：C 的祖先链含 B、A
        List<Node> targetLine = List.of(b, a);
        List<Node> subtreeOfA = List.of(a, b, c);
        assertThatThrownBy(() -> CategoryRules.validateMove(a, c, targetLine, subtreeOfA))
                .isInstanceOf(CategoryException.class);
    }

    @Test
    @DisplayName("TC-004 把 B 挂到 A 下（原关系）与把 C 移到根均为合法移动")
    void legalMovesAccepted() {
        Node a = node(1, 0, 1, true);
        Node b = node(2, 1, 2, true);
        Node c = node(3, 2, 3, true);
        assertThat(CategoryRules.validateMove(b, a, List.of(), List.of(b, c)))
                .isEqualTo(2);
        // C 移到一级：自身新 level=1，子树仅自身，合法
        assertThat(CategoryRules.validateMove(c, null, List.of(), List.of(c)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("TC-002 移动带子树节点会导致层级超限时拒绝（2 级节点下移到 2 级父，其下已有 3 级子）")
    void movingSubtreeBeyondMaxRejected() {
        // x(2 级,父为 rootA) 下有 y(3 级)；尝试把 x 移到另一 2 级节点 b 下 → x 变 3 级、y 将变 4 级
        Node rootA = node(1, 0, 1, true);
        Node x = node(4, 1, 2, true);
        Node y = node(5, 4, 3, true);
        Node b = node(2, 1, 2, true);
        // b 的祖先链含 rootA
        assertThatThrownBy(() ->
                CategoryRules.validateMove(x, b, List.of(rootA), List.of(x, y)))
                .isInstanceOf(CategoryException.class);
    }

    @Test
    @DisplayName("TC-006 移动到禁用的新父下被拒")
    void moveToDisabledTargetRejected() {
        Node self = node(10, 0, 1, true);
        Node disabled = node(20, 0, 1, false);
        assertThatThrownBy(() -> CategoryRules.validateMove(self, disabled, List.of(), List.of(self)))
                .isInstanceOf(CategoryException.class);
    }
}
