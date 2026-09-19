package com.ai.mall.product.interfaces.rest.admin.dto;

/**
 * 图片上传接口 DTO（CHG-0023 STORY-007-01-01-02）。
 */
public final class ProductImageDtos {

    private ProductImageDtos() {
    }

    /**
     * 上传成功出参：可公开访问的图片 URL。
     */
    public record ImageUploadResponse(String url) {
    }
}
