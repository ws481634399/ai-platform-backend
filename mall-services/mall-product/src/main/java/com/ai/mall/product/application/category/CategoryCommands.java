package com.ai.mall.product.application.category;

/**
 * 分类应用层命令（接口层 DTO 与领域之间的入参契约）。
 */
public final class CategoryCommands {

    private CategoryCommands() {
    }

    public record CreateCategoryCommand(String name, Long parentId, Integer sort) {
    }

    public record UpdateCategoryCommand(String name, Long parentId, Integer sort) {
    }

    public record ChangeCategoryStatusCommand(String status) {
    }
}
