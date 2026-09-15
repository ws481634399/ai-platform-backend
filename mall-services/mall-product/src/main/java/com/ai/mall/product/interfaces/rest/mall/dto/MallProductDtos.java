package com.ai.mall.product.interfaces.rest.mall.dto;

import com.ai.mall.common.web.annotation.StringId;
import java.util.List;

/**
 * 商城商品查询 DTO。
 *
 * <p>CHG-0015：所有业务 ID 标注 {@link StringId} 输出字符串；价区/金额/分页保持 number。
 */
public final class MallProductDtos {

    private MallProductDtos() {}

    public record PageView<T>(List<T> records, long total, int page, int size) {}

    public record MallProductListItemView(
            @StringId long id,
            String productCode,
            String productName,
            String subtitle,
            @StringId long categoryId,
            @StringId long brandId,
            String mainImageUrl,
            long minPrice,
            long maxPrice,
            String status
    ) {}

    public record MallProductDetailView(
            @StringId long id,
            String productCode,
            String productName,
            String subtitle,
            String description,
            @StringId long categoryId,
            @StringId long brandId,
            String mainImageUrl,
            List<ImageView> images,
            List<AttributeView> attributes,
            List<SkuView> skus,
            String status
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
