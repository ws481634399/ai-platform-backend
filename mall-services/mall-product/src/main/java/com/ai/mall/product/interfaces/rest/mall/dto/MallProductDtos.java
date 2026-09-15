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
            String brandName,
            List<CategoryPathView> categoryPath,
            String mainImageUrl,
            List<ImageView> images,
            List<AttributeView> attributes,
            List<SkuView> skus,
            List<String> dimensionsOrder,
            List<SpecDimensionView> specDimensions,
            java.util.Map<String, SkuIndexEntryView> skuIndex,
            String status
    ) {}

    /** 分类路径：从根到当前分类的链路（≤3 层）。 */
    public record CategoryPathView(@StringId long id, String name) {}

    /** 规格维度：维度名 + 去重保序的值列表。 */
    public record SpecDimensionView(String name, List<String> values) {}

    /** SKU 组合索引条目：组合键 → SKU 信息。 */
    public record SkuIndexEntryView(
            @StringId long skuId,
            long priceFen,
            String imageUrl,
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
