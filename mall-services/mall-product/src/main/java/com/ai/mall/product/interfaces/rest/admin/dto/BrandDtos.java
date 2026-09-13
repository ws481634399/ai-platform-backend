package com.ai.mall.product.interfaces.rest.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 品牌管理端接口 DTO 集合（含通用分页视图）。
 */
public final class BrandDtos {

    private BrandDtos() {
    }

    public record CreateBrandRequest(
            @NotBlank(message = "品牌名称不能为空") @Size(max = 64, message = "品牌名称最长 64 个字符") String name,
            @Size(max = 512, message = "Logo 地址最长 512 个字符") String logo,
            @Size(max = 255, message = "品牌描述最长 255 个字符") String description,
            Integer sort) {
    }

    public record UpdateBrandRequest(
            @NotBlank(message = "品牌名称不能为空") @Size(max = 64, message = "品牌名称最长 64 个字符") String name,
            @Size(max = 512, message = "Logo 地址最长 512 个字符") String logo,
            @Size(max = 255, message = "品牌描述最长 255 个字符") String description,
            Integer sort) {
    }

    public record BrandStatusRequest(@NotBlank(message = "状态不能为空") String status) {
    }

    public record BrandView(long id, String name, String logo, String description, int sort, String status) {
    }

    /** 统一分页响应视图：records/total/page/size。 */
    public record PageView<T>(List<T> records, long total, int page, int size) {
    }
}
