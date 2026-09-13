package com.ai.mall.product.domain.category;

import java.util.List;

/**
 * 分类跨实体不变量的纯领域判定（无框架/无 IO，便于参数化单测）。
 *
 * <p>输入均为已由仓储加载完成的不可变快照：
 * {@link Node} 只暴露规则需要的 id / parentId / level / 启用状态。
 */
public final class CategoryRules {

    private CategoryRules() {
    }

    /** 规则所需的节点最小视图（PO/聚合均可适配）。 */
    public record Node(long id, long parentId, int level, boolean enabled) {
    }

    /**
     * 创建时推导层级并校验父级。
     *
     * @param parent 父分类；null 表示建一级分类
     * @return 新分类的 level
     */
    public static int levelForCreate(Node parent) {
        if (parent == null) {
            return CategoryLevel.MIN_LEVEL;
        }
        if (!parent.enabled()) {
            throw CategoryException.parentDisabled();
        }
        if (parent.level() >= CategoryLevel.MAX_LEVEL) {
            throw CategoryException.levelExceeded();
        }
        return parent.level() + 1;
    }

    /**
     * 移动/更新父级的全部跨实体校验，并返回目标层级。
     *
     * @param self       被移动节点
     * @param target     新父节点；null 表示移到一级（parentId=0）
     * @param targetLine 新父节点到根的祖先链（不含 target 自身，按 parentId 向上，可为空）
     * @param subtree    以 self 为根的整棵子树（含 self 自身）
     * @return self 移动后的新 level
     */
    public static int validateMove(Node self, Node target, List<Node> targetLine, List<Node> subtree) {
        if (target == null) {
            return validateRootMove(self, subtree);
        }
        if (target.id() == self.id()) {
            throw CategoryException.selfReference();
        }
        if (!target.enabled()) {
            throw CategoryException.parentDisabled();
        }
        for (Node ancestor : targetLine) {
            if (ancestor.id() == self.id()) {
                throw CategoryException.cycle();
            }
        }
        int newLevel = target.level() + 1;
        int maxRelativeDepth = subtree.stream()
                .mapToInt(node -> node.level() - self.level())
                .max().orElse(0);
        if (newLevel + maxRelativeDepth > CategoryLevel.MAX_LEVEL) {
            throw CategoryException.levelExceeded();
        }
        return newLevel;
    }

    /** 移到一级：自身成为根，子树整体跟随，只需校验子树总深度不超上限。 */
    private static int validateRootMove(Node self, List<Node> subtree) {
        int maxRelativeDepth = subtree.stream()
                .mapToInt(node -> node.level() - self.level())
                .max().orElse(0);
        if (CategoryLevel.MIN_LEVEL + maxRelativeDepth > CategoryLevel.MAX_LEVEL) {
            throw CategoryException.levelExceeded();
        }
        return CategoryLevel.MIN_LEVEL;
    }
}
