package com.ai.mall.product.application.product;

import com.ai.mall.product.domain.product.ProductAttribute;
import com.ai.mall.product.domain.product.ProductImage;
import java.util.List;

/**
 * 商品应用服务命令与查询对象。
 */
public final class ProductCommands {

    private ProductCommands() {}

    public record CreateProductCommand(
            String code,
            String name,
            String subtitle,
            String description,
            long categoryId,
            long brandId,
            List<ImageParam> images,
            List<AttributeParam> attributes,
            List<CreateSkuCommand> skus
    ) {}

    public record UpdateProductCommand(
            String name,
            String subtitle,
            String description,
            long categoryId,
            long brandId,
            List<ImageParam> images,
            List<AttributeParam> attributes
    ) {}

    public record ChangeProductStatusCommand(String status) {}

    public record ProductPageQuery(String keyword, Long categoryId, Long brandId, String status, Integer page, Integer size) {}

    public record ImageParam(String objectKey, String imageUrl, String imageType, int sortOrder, boolean mainFlag) {}

    public record AttributeParam(String name, String value, int sortOrder) {}

    public record CreateSkuCommand(
            String skuCode,
            List<SpecificationParam> specifications,
            long salePriceInCents,
            String mainImageUrl
    ) {}

    public record UpdateSkuCommand(
            long salePriceInCents,
            String mainImageUrl
    ) {}

    public record ChangeSkuStatusCommand(String status) {}

    public record SpecificationParam(String name, String value) {}
}
