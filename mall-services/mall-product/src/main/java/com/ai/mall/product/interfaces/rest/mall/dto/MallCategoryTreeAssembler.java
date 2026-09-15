package com.ai.mall.product.interfaces.rest.mall.dto;

import com.ai.mall.product.domain.category.Category;
import com.ai.mall.product.domain.category.CategoryLevel;
import com.ai.mall.product.interfaces.rest.mall.dto.MallCatalogDtos.CategoryNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商城公开分类树装配：从全量平铺分类中构建仅含启用节点的树。
 *
 * <p>剪枝规则：禁用节点不入树且不递归其子节点——禁用父节点整枝剪除，
 * 即使子节点为 ENABLED 也随枝剪掉（避免出现孤儿启用节点）。
 * 每层按 sort、id 稳定排序。
 */
public final class MallCategoryTreeAssembler {

    private static final Comparator<Category> BY_SORT_ID =
            Comparator.comparingInt(Category::getSort).thenComparingLong(Category::getId);

    private MallCategoryTreeAssembler() {
    }

    public static List<CategoryNode> toTree(List<Category> flat) {
        Map<Long, List<Category>> childrenByParent = new HashMap<>();
        for (Category category : flat) {
            childrenByParent.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
        }
        childrenByParent.values().forEach(children -> children.sort(BY_SORT_ID));
        return buildChildren(CategoryLevel.ROOT_PARENT_ID, childrenByParent);
    }

    private static List<CategoryNode> buildChildren(long parentId, Map<Long, List<Category>> childrenByParent) {
        List<CategoryNode> nodes = new ArrayList<>();
        for (Category category : childrenByParent.getOrDefault(parentId, List.of())) {
            // 禁用节点不入树且不递归其子（整枝剪除）
            if (!category.isEnabled()) {
                continue;
            }
            nodes.add(new CategoryNode(
                    category.getId(), category.getName(), category.getSort(),
                    buildChildren(category.getId(), childrenByParent)));
        }
        return nodes;
    }
}
