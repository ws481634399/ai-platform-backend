package com.ai.mall.product.interfaces.rest.admin.dto;

import com.ai.mall.common.web.annotation.StringId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * 商品管理端 DTO。
 *
 * <p>CHG-0015：业务 ID 标注 {@link StringId}；入参 ID 保持 Long（原生兼容字符串/数字双形态）。
 */
public final class ProductDtos {

    private ProductDtos() {}

    public record PageView<T>(List<T> records, long total, int page, int size) {}

    /** 新建实体后的 ID 回显（字符串，避免 JS 精度丢失）。 */
    public record IdView(@StringId long id) {}

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
            @StringId long id,
            String code,
            String name,
            String subtitle,
            String description,
            @StringId long categoryId,
            @StringId long brandId,
            String status,
            String mainImageUrl,
            List<ImageView> images,
            List<AttributeView> attributes,
            List<SkuView> skus
    ) {}

    public record ImageView(
            @StringId long id,
            String objectKey,
            String imageUrl,
            String imageType,
            int sortOrder,
            boolean mainFlag
    ) {}

    public record AttributeView(@StringId long id, String name, String value, int sortOrder) {}

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
            @StringId long id,
            String skuCode,
            List<SpecificationView> specifications,
            long salePriceInCents,
            String status,
            String mainImageUrl
    ) {}

    public record SpecificationView(String name, String value) {}
}
