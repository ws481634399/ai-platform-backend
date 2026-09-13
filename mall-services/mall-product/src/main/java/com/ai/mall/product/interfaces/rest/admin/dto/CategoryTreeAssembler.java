package com.ai.mall.product.interfaces.rest.admin.dto;

import com.ai.mall.product.domain.category.Category;
import com.ai.mall.product.domain.category.CategoryLevel;
import com.ai.mall.product.interfaces.rest.admin.dto.CategoryDtos.CategoryTreeView;
import com.ai.mall.product.interfaces.rest.admin.dto.CategoryDtos.CategoryView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类平铺列表 → 多级树视图（含禁用节点；每层按 sort、id 稳定排序）。
 */
public final class CategoryTreeAssembler {

    private static final Comparator<Category> BY_SORT_ID =
            Comparator.comparingInt(Category::getSort).thenComparingLong(Category::getId);

    private CategoryTreeAssembler() {
    }

    public static List<CategoryTreeView> toTree(List<Category> flat) {
        Map<Long, List<Category>> childrenByParent = new HashMap<>();
        for (Category category : flat) {
            childrenByParent.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
        }
        childrenByParent.values().forEach(children -> children.sort(BY_SORT_ID));
        return buildChildren(CategoryLevel.ROOT_PARENT_ID, childrenByParent);
    }

    public static CategoryView toView(Category category) {
        return new CategoryView(category.getId(), category.getName(), category.getParentId(),
                category.getLevel(), category.getSort(), category.getStatus().name());
    }

    private static List<CategoryTreeView> buildChildren(long parentId, Map<Long, List<Category>> childrenByParent) {
        List<CategoryTreeView> nodes = new ArrayList<>();
        for (Category category : childrenByParent.getOrDefault(parentId, List.of())) {
            nodes.add(new CategoryTreeView(
                    category.getId(), category.getName(), category.getParentId(), category.getLevel(),
                    category.getSort(), category.getStatus().name(),
                    buildChildren(category.getId(), childrenByParent)));
        }
        return nodes;
    }
}
