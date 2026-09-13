package com.ai.mall.product.infrastructure.persistence.product;

import com.ai.mall.product.domain.product.Product;
import com.ai.mall.product.domain.product.ProductAttribute;
import com.ai.mall.product.domain.product.ProductImage;
import com.ai.mall.product.domain.product.ProductRepository;
import com.ai.mall.product.domain.product.ProductStatus;
import com.ai.mall.product.domain.product.Sku;
import com.ai.mall.product.domain.product.Specification;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProductMapper productMapper;
    private final ProductImageMapper imageMapper;
    private final ProductAttributeMapper attributeMapper;
    private final SkuMapper skuMapper;

    public ProductRepositoryImpl(ProductMapper productMapper, ProductImageMapper imageMapper,
                                  ProductAttributeMapper attributeMapper, SkuMapper skuMapper) {
        this.productMapper = productMapper;
        this.imageMapper = imageMapper;
        this.attributeMapper = attributeMapper;
        this.skuMapper = skuMapper;
    }

    @Override
    public Optional<Product> findById(long id) {
        ProductPo po = productMapper.selectById(id);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po, loadImages(id), loadAttributes(id), loadSkus(id)));
    }

    @Override
    public boolean existsByCode(String code, Long excludeId) {
        LambdaQueryWrapper<ProductPo> wrapper = new LambdaQueryWrapper<ProductPo>()
                .apply("LOWER(product_code) = LOWER({0})", code);
        if (excludeId != null) {
            wrapper.ne(ProductPo::getId, excludeId);
        }
        return productMapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean existsBySkuCode(String skuCode, Long excludeSkuId) {
        LambdaQueryWrapper<SkuPo> wrapper = new LambdaQueryWrapper<SkuPo>()
                .apply("LOWER(sku_code) = LOWER({0})", skuCode);
        if (excludeSkuId != null) {
            wrapper.ne(SkuPo::getId, excludeSkuId);
        }
        return skuMapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean existsBySkuId(long skuId) {
        return skuMapper.selectById(skuId) != null;
    }

    @Override
    public ProductPageResult page(ProductPageQuery query) {
        LambdaQueryWrapper<ProductPo> wrapper = new LambdaQueryWrapper<ProductPo>()
                .like(query.keyword() != null && !query.keyword().isBlank(), ProductPo::getProductName, query.keyword())
                .eq(query.categoryId() != null, ProductPo::getCategoryId, query.categoryId())
                .eq(query.brandId() != null, ProductPo::getBrandId, query.brandId())
                .eq(query.status() != null && !query.status().isBlank(), ProductPo::getStatus, query.status())
                .orderByDesc(ProductPo::getCreatedAt);
        Page<ProductPo> result = productMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        List<Product> records = result.getRecords().stream()
                .map(po -> toDomain(po, loadImages(po.getId()), loadAttributes(po.getId()), loadSkus(po.getId())))
                .toList();
        return new ProductPageResult(records, result.getTotal(), query.page(), query.size());
    }

    @Override
    public ProductPageResult mallPage(ProductPageQuery query) {
        LambdaQueryWrapper<ProductPo> wrapper = new LambdaQueryWrapper<ProductPo>()
                .like(query.keyword() != null && !query.keyword().isBlank(), ProductPo::getProductName, query.keyword())
                .eq(query.categoryId() != null, ProductPo::getCategoryId, query.categoryId())
                .eq(query.brandId() != null, ProductPo::getBrandId, query.brandId())
                .eq(ProductPo::getStatus, ProductStatus.ON_SALE.name())
                .orderByDesc(ProductPo::getCreatedAt);
        Page<ProductPo> result = productMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        List<Product> records = result.getRecords().stream()
                .map(po -> toDomain(po, loadImages(po.getId()), loadAttributes(po.getId()), loadSkus(po.getId())))
                .toList();
        return new ProductPageResult(records, result.getTotal(), query.page(), query.size());
    }

    @Override
    public void insert(Product product) {
        ProductPo po = toPo(product);
        productMapper.insert(po);
        product.assignCreated(po.getId(), po.getCreatedAt() != null ? po.getCreatedAt() : Instant.now());
        saveImages(product);
        saveAttributes(product);
        saveSkus(product);
    }

    @Override
    public boolean update(Product product) {
        product.touch(Instant.now());
        ProductPo po = toPo(product);
        int rows = productMapper.updateById(po);
        if (rows == 1) {
            replaceImages(product);
            replaceAttributes(product);
            replaceSkus(product);
        }
        return rows == 1;
    }

    private List<ProductImage> loadImages(long productId) {
        return imageMapper.selectList(new LambdaQueryWrapper<ProductImagePo>()
                        .eq(ProductImagePo::getProductId, productId)
                        .orderByAsc(ProductImagePo::getSortOrder))
                .stream().map(this::imageToDomain).toList();
    }

    private List<ProductAttribute> loadAttributes(long productId) {
        return attributeMapper.selectList(new LambdaQueryWrapper<ProductAttributePo>()
                        .eq(ProductAttributePo::getProductId, productId)
                        .orderByAsc(ProductAttributePo::getSortOrder))
                .stream().map(this::attributeToDomain).toList();
    }

    private void saveImages(Product product) {
        for (ProductImage image : product.getImages()) {
            ProductImagePo po = imageToPo(image, product.getId());
            imageMapper.insert(po);
        }
    }

    private void replaceImages(Product product) {
        imageMapper.delete(new LambdaQueryWrapper<ProductImagePo>()
                .eq(ProductImagePo::getProductId, product.getId()));
        saveImages(product);
    }

    private void saveAttributes(Product product) {
        for (ProductAttribute attr : product.getAttributes()) {
            ProductAttributePo po = attributeToPo(attr, product.getId());
            attributeMapper.insert(po);
        }
    }

    private void replaceAttributes(Product product) {
        attributeMapper.delete(new LambdaQueryWrapper<ProductAttributePo>()
                .eq(ProductAttributePo::getProductId, product.getId()));
        saveAttributes(product);
    }

    private List<Sku> loadSkus(long productId) {
        return skuMapper.selectList(new LambdaQueryWrapper<SkuPo>()
                        .eq(SkuPo::getProductId, productId)
                        .orderByAsc(SkuPo::getId))
                .stream().map(this::skuToDomain).toList();
    }

    private void saveSkus(Product product) {
        for (Sku sku : product.getSkus()) {
            SkuPo po = skuToPo(sku, product.getId());
            skuMapper.insert(po);
            sku.assignCreated(po.getId(), po.getCreatedAt() != null ? po.getCreatedAt() : Instant.now());
        }
    }

    private void replaceSkus(Product product) {
        skuMapper.delete(new LambdaQueryWrapper<SkuPo>()
                .eq(SkuPo::getProductId, product.getId()));
        saveSkus(product);
    }

    private Product toDomain(ProductPo po, List<ProductImage> images, List<ProductAttribute> attributes, List<Sku> skus) {
        Instant created = po.getCreatedAt() == null ? Instant.EPOCH : po.getCreatedAt();
        Instant updated = po.getUpdatedAt() == null ? created : po.getUpdatedAt();
        return Product.reconstitute(po.getId(), po.getProductCode(), po.getProductName(),
                po.getSubtitle(), po.getDescription(), po.getCategoryId(), po.getBrandId(),
                po.getStatus(), po.getMainImageUrl(), images, attributes, skus, created, updated);
    }

    private ProductPo toPo(Product product) {
        Instant now = Instant.now();
        ProductPo po = new ProductPo();
        po.setId(product.getId() == 0L ? null : product.getId());
        po.setProductCode(product.getCode());
        po.setProductName(product.getName());
        po.setSubtitle(product.getSubtitle());
        po.setDescription(product.getDescription());
        po.setCategoryId(product.getCategoryId());
        po.setBrandId(product.getBrandId());
        po.setStatus(product.getStatus().name());
        po.setMainImageUrl(product.getMainImageUrl());
        po.setSalesCount(0L);
        po.setCreatedAt(product.getCreatedAt() != null ? product.getCreatedAt() : now);
        po.setUpdatedAt(product.getUpdatedAt() != null ? product.getUpdatedAt() : now);
        po.setVersion(0L);
        po.setDeleted(0);
        return po;
    }

    private ProductImage imageToDomain(ProductImagePo po) {
        return new ProductImage(po.getId(), po.getObjectKey(), po.getImageUrl(),
                ProductImage.ImageType.valueOf(po.getImageType()),
                po.getSortOrder() == null ? 0 : po.getSortOrder(),
                po.getMainFlag() != null && po.getMainFlag() == 1);
    }

    private ProductImagePo imageToPo(ProductImage image, long productId) {
        ProductImagePo po = new ProductImagePo();
        po.setProductId(productId);
        po.setObjectKey(image.objectKey());
        po.setImageUrl(image.imageUrl());
        po.setImageType(image.imageType().name());
        po.setSortOrder(image.sortOrder());
        po.setMainFlag(image.mainFlag() ? 1 : 0);
        po.setCreatedAt(Instant.now());
        return po;
    }

    private ProductAttribute attributeToDomain(ProductAttributePo po) {
        return new ProductAttribute(po.getId(), po.getAttributeName(), po.getAttributeValue(),
                po.getSortOrder() == null ? 0 : po.getSortOrder());
    }

    private ProductAttributePo attributeToPo(ProductAttribute attr, long productId) {
        ProductAttributePo po = new ProductAttributePo();
        po.setProductId(productId);
        po.setAttributeName(attr.name());
        po.setAttributeValue(attr.value());
        po.setSortOrder(attr.sortOrder());
        po.setCreatedAt(Instant.now());
        po.setUpdatedAt(Instant.now());
        return po;
    }

    private Sku skuToDomain(SkuPo po) {
        List<Specification> specs = parseSpecifications(po.getSpecificationContent());
        Instant created = po.getCreatedAt() == null ? Instant.EPOCH : po.getCreatedAt();
        Instant updated = po.getUpdatedAt() == null ? created : po.getUpdatedAt();
        return Sku.reconstitute(po.getId(), po.getSkuCode(), specs, po.getSpecificationHash(),
                po.getSalePrice(), po.getStatus(), po.getMainImageUrl(), created, updated);
    }

    private SkuPo skuToPo(Sku sku, long productId) {
        Instant now = Instant.now();
        SkuPo po = new SkuPo();
        po.setId(sku.getId() == 0L ? null : sku.getId());
        po.setProductId(productId);
        po.setSkuCode(sku.getCode());
        po.setSalePrice(sku.getSalePrice().amountInCents());
        po.setStatus(sku.getStatus().name());
        po.setMainImageUrl(sku.getMainImageUrl());
        po.setSpecificationContent(toSpecJson(sku.getSpecifications()));
        po.setSpecificationHash(sku.getSpecificationHash().value());
        po.setCreatedAt(sku.getCreatedAt() != null ? sku.getCreatedAt() : now);
        po.setUpdatedAt(sku.getUpdatedAt() != null ? sku.getUpdatedAt() : now);
        po.setVersion(0L);
        po.setDeleted(0);
        return po;
    }

    private List<Specification> parseSpecifications(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            Map<String, String> map = OBJECT_MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
            return map.entrySet().stream()
                    .map(e -> new Specification(e.getKey(), e.getValue()))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("解析 SKU 规格 JSON 失败: " + json, e);
        }
    }

    private String toSpecJson(List<Specification> specs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Specification spec : specs) {
            map.put(spec.name(), spec.value());
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(map);
            return json;
        } catch (Exception e) {
            throw new IllegalStateException("序列化 SKU 规格 JSON 失败", e);
        }
    }
}
