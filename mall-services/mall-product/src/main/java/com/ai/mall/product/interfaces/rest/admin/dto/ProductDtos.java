package com.ai.mall.product.interfaces.rest.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * 商品管理端 DTO。
 */
public final class ProductDtos {

    private ProductDtos() {}

    public record PageView<T>(List<T> records, long total, int page, int size) {}

    public record CreateProductRequest(
            @NotBlank String code,
            @NotBlank String name,
            String subtitle,
            String description,
            @NotNull Long categoryId,
            @NotNull Long brandId,
            List<ImageRequest> images,
            List<AttributeRequest> attributes,
            @NotEmpty List<@Valid CreateSkuRequest> skus
    ) {}

    public record UpdateProductRequest(
            @NotBlank String name,
            String subtitle,
            String description,
            @NotNull Long categoryId,
            @NotNull Long brandId,
            List<ImageRequest> images,
            List<AttributeRequest> attributes
    ) {}

    public record ProductStatusRequest(@NotBlank String status) {}

    public record ImageRequest(
            @NotBlank String objectKey,
            @NotBlank String imageUrl,
            @NotBlank String imageType,
            int sortOrder,
            boolean mainFlag
    ) {}

    public record AttributeRequest(
            @NotBlank String name,
            @NotBlank String value,
            int sortOrder
    ) {}

    public record ProductView(
            long id,
            String code,
            String name,
            String subtitle,
            String description,
            long categoryId,
            long brandId,
            String status,
            String mainImageUrl,
            List<ImageView> images,
            List<AttributeView> attributes,
            List<SkuView> skus
    ) {}

    public record ImageView(
            long id,
            String objectKey,
            String imageUrl,
            String imageType,
            int sortOrder,
            boolean mainFlag
    ) {}

    public record AttributeView(long id, String name, String value, int sortOrder) {}

    public record CreateSkuRequest(
            @NotBlank String skuCode,
            @NotEmpty List<@Valid SpecificationRequest> specifications,
            @PositiveOrZero long salePriceInCents,
            String mainImageUrl
    ) {}

    public record UpdateSkuRequest(long salePriceInCents, String mainImageUrl) {}

    public record SkuStatusRequest(@NotBlank String status) {}

    public record SpecificationRequest(@NotBlank String name, @NotBlank String value) {}

    public record SkuView(
            long id,
            String skuCode,
            List<SpecificationView> specifications,
            long salePriceInCents,
            String status,
            String mainImageUrl
    ) {}

    public record SpecificationView(String name, String value) {}
}
