package com.ai.mall.product.application.brand;

/**
 * 品牌管理命令与查询入参。
 */
public final class BrandCommands {

    private BrandCommands() {
    }

    public record CreateBrandCommand(String name, String logo, String description, Integer sort) {
    }

    public record UpdateBrandCommand(String name, String logo, String description, Integer sort) {
    }

    public record ChangeBrandStatusCommand(String status) {
    }

    public record BrandPageQuery(String keyword, String status, Integer page, Integer size) {
    }
}
