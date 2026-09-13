package com.ai.mall.product.interfaces.rest.mall.dto;

import java.util.List;

/**
 * 商城商品查询 DTO。
 */
public final class MallProductDtos {

    private MallProductDtos() {}

    public record PageView<T>(List<T> records, long total, int page, int size) {}

    public record MallProductListItemView(
            long id,
            String productCode,
            String productName,
            String subtitle,
            long categoryId,
            long brandId,
            String mainImageUrl,
            Long minPrice,
            Long maxPrice,
            String status
    ) {}

    public record MallProductDetailView(
            long id,
            String productCode,
            String productName,
            String subtitle,
            String description,
            long categoryId,
            long brandId,
            String mainImageUrl,
            List<ImageView> images,
            List<AttributeView> attributes,
            List<SkuView> skus,
            String status
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
