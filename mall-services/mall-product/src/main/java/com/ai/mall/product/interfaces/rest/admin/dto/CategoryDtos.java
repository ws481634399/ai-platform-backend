package com.ai.mall.product.interfaces.rest.admin.dto;

import com.ai.mall.common.web.annotation.StringId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 分类管理端接口 DTO 集合。
 */
public final class CategoryDtos {

    private CategoryDtos() {
    }

    /** 新建实体后的 ID 回显（字符串，避免 JS 精度丢失）。 */
    public record IdView(@StringId long id) {
    }

    public record CreateCategoryRequest(
            @NotBlank(message = "分类名称不能为空") @Size(max = 32, message = "分类名称最长 32 个字符") String name,
            Long parentId,
            Integer sort) {
    }

    public record UpdateCategoryRequest(
            @NotBlank(message = "分类名称不能为空") @Size(max = 32, message = "分类名称最长 32 个字符") String name,
            Long parentId,
            Integer sort) {
    }

    public record CategoryStatusRequest(@NotBlank(message = "状态不能为空") String status) {
    }

    public record CategoryView(@StringId long id, String name, @StringId long parentId, int level, int sort,
                              String status) {
    }

    public record CategoryTreeView(@StringId long id, String name, @StringId long parentId, int level, int sort,
                                   String status, List<CategoryTreeView> children) {
    }
}
