package com.ai.mall.product.infrastructure.persistence.product;

import com.ai.mall.product.domain.product.Product;
import com.ai.mall.product.domain.product.ProductAttribute;
import com.ai.mall.product.domain.product.ProductImage;
import com.ai.mall.product.domain.product.ProductRepository;
import com.ai.mall.product.domain.product.ProductStatus;
import com.ai.mall.product.domain.product.Sku;
import com.ai.mall.product.domain.product.SkuStatus;
import com.ai.mall.product.domain.product.Specification;
import com.ai.mall.product.domain.product.MallProductSort;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
    public List<Product> findBySkuIds(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        // 1) 一次 IN 查询命中 SKU（显式过滤软删）
        List<SkuPo> hitSkus = skuMapper.selectList(new LambdaQueryWrapper<SkuPo>()
                .in(SkuPo::getId, skuIds).eq(SkuPo::getDeleted, 0));
        if (hitSkus.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = hitSkus.stream().map(SkuPo::getProductId).distinct().toList();
        // 2) 一次 IN 查询命中商品（显式过滤软删）
        List<ProductPo> products = productMapper.selectList(new LambdaQueryWrapper<ProductPo>()
                .in(ProductPo::getId, productIds).eq(ProductPo::getDeleted, 0));
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> liveProductIds = products.stream().map(ProductPo::getId).toList();
        // 3) 图片/属性/SKU 各一次批量装载，按 productId 分组（禁止 N+1），分组时即转领域模型
        Map<Long, List<ProductImage>> imageMap = imageMapper.selectList(new LambdaQueryWrapper<ProductImagePo>()
                        .in(ProductImagePo::getProductId, liveProductIds)
                        .orderByAsc(ProductImagePo::getSortOrder))
                .stream().collect(Collectors.groupingBy(ProductImagePo::getProductId,
                        Collectors.mapping(this::imageToDomain, Collectors.toList())));
        Map<Long, List<ProductAttribute>> attrMap = attributeMapper.selectList(new LambdaQueryWrapper<ProductAttributePo>()
                        .in(ProductAttributePo::getProductId, liveProductIds)
                        .orderByAsc(ProductAttributePo::getSortOrder))
                .stream().collect(Collectors.groupingBy(ProductAttributePo::getProductId,
                        Collectors.mapping(this::attributeToDomain, Collectors.toList())));
        Map<Long, List<Sku>> skuMap = skuMapper.selectList(new LambdaQueryWrapper<SkuPo>()
                        .in(SkuPo::getProductId, liveProductIds).eq(SkuPo::getDeleted, 0)
                        .orderByAsc(SkuPo::getId))
                .stream().collect(Collectors.groupingBy(SkuPo::getProductId,
                        Collectors.mapping(this::skuToDomain, Collectors.toList())));
        return products.stream()
                .map(po -> toDomain(po,
                        imageMap.getOrDefault(po.getId(), List.of()),
                        attrMap.getOrDefault(po.getId(), List.of()),
                        skuMap.getOrDefault(po.getId(), List.of())))
                .toList();
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
        // CHG-0017：商城列表走自定义 SQL——LEFT JOIN 启用 SKU 价区派生表，
        // pr.product_id IS NOT NULL 等价 EXISTS 启用 SKU；价区排序走派生表列（禁止内存排序）。
        Page<ProductPo> result = productMapper.selectMallPage(
                new Page<>(query.page(), query.size()),
                query.categoryIds(), query.brandIds(), query.keyword(),
                query.sort() == null ? MallProductSort.DEFAULT.name() : query.sort().name());
        List<Product> records = result.getRecords().stream()
                .map(po -> toDomain(po, loadImages(po.getId()), loadAttributes(po.getId()), loadSkus(po.getId())))
                .toList();
        return new ProductPageResult(records, result.getTotal(), query.page(), query.size(),
                loadPriceRanges(records));
    }

    /**
     * 当页商品启用 SKU 价区：一次分组 SQL 批量加载（禁止 N+1）。
     */
    private Map<Long, PriceRange> loadPriceRanges(List<Product> records) {
        if (records.isEmpty()) {
            return Map.of();
        }
        List<Long> productIds = records.stream().map(Product::getId).toList();
        Map<Long, PriceRange> priceRanges = new LinkedHashMap<>();
        for (PriceRangePo po : skuMapper.selectEnabledPriceRanges(productIds)) {
            priceRanges.put(po.getProductId(),
                    new PriceRange(po.getMinPrice(), po.getMaxPrice()));
        }
        return priceRanges;
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
