package com.ai.mall.product.domain.product;

import com.ai.mall.product.domain.product.event.ProductDomainEvent;
import com.ai.mall.product.domain.product.event.ProductPublishedDomainEvent;
import com.ai.mall.product.domain.product.event.ProductUnpublishedDomainEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品（SPU）聚合根。
 * 负责商品基本信息、图片、属性、状态生命周期；SKU 集合由本聚合持有，
 * SKU 相关行为（addSku/updateSku 等）在 STORY-002-02-02-01 中补充。
 */
public class Product {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_SUBTITLE_LENGTH = 255;
    private static final int MAX_CODE_LENGTH = 64;

    private long id;
    private String code;
    private String name;
    private String subtitle;
    private String description;
    private long categoryId;
    private long brandId;
    private ProductStatus status;
    private String mainImageUrl;
    private List<ProductImage> images;
    private List<ProductAttribute> attributes;
    private List<Sku> skus;
    private Instant createdAt;
    private Instant updatedAt;
    private final List<ProductDomainEvent> domainEvents = new ArrayList<>();

    private Product(long id, String code, String name, String subtitle, String description,
                    long categoryId, long brandId, ProductStatus status, String mainImageUrl,
                    List<ProductImage> images, List<ProductAttribute> attributes, List<Sku> skus,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.subtitle = subtitle;
        this.description = description;
        this.categoryId = categoryId;
        this.brandId = brandId;
        this.status = status;
        this.mainImageUrl = mainImageUrl;
        this.images = images == null ? new ArrayList<>() : new ArrayList<>(images);
        this.attributes = attributes == null ? new ArrayList<>() : new ArrayList<>(attributes);
        this.skus = skus == null ? new ArrayList<>() : new ArrayList<>(skus);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 创建新商品，状态 DRAFT。 */
    public static Product createNew(String code, String name, String subtitle, String description,
                                    long categoryId, long brandId,
                                    List<ProductImage> images, List<ProductAttribute> attributes) {
        Product product = new Product(0L, normalizeCode(code), normalizeName(name),
                normalizeSubtitle(subtitle), normalizeDescription(description),
                categoryId, brandId, ProductStatus.DRAFT, null,
                images == null ? List.of() : images,
                attributes == null ? List.of() : attributes,
                new ArrayList<>(), null, null);
        product.recomputeMainImage();
        return product;
    }

    /** 持久化重建。 */
    public static Product reconstitute(long id, String code, String name, String subtitle, String description,
                                       long categoryId, long brandId, String status, String mainImageUrl,
                                       List<ProductImage> images, List<ProductAttribute> attributes, List<Sku> skus,
                                       Instant createdAt, Instant updatedAt) {
        return new Product(id, code, name, subtitle, description, categoryId, brandId,
                ProductStatus.from(status), mainImageUrl, images, attributes, skus, createdAt, updatedAt);
    }

    /** 更新基本信息（名称/副标题/描述/分类/品牌）。 */
    public void updateBasicInfo(String name, String subtitle, String description,
                                long categoryId, long brandId) {
        this.name = normalizeName(name);
        this.subtitle = normalizeSubtitle(subtitle);
        this.description = normalizeDescription(description);
        this.categoryId = categoryId;
        this.brandId = brandId;
    }

    /** 替换图片集合并重算主图。 */
    public void replaceImages(List<ProductImage> images) {
        List<ProductImage> newImages = images == null ? List.of() : images;
        long mainCount = newImages.stream().filter(ProductImage::mainFlag).count();
        if (mainCount > 1) {
            throw ProductException.mainImageDuplicated();
        }
        this.images = new ArrayList<>(newImages);
        recomputeMainImage();
    }

    /** 替换属性集合。 */
    public void replaceAttributes(List<ProductAttribute> attributes) {
        this.attributes = attributes == null ? new ArrayList<>() : new ArrayList<>(attributes);
    }

    /** 禁用商品。 */
    public void disable() {
        this.status = ProductStatus.DISABLED;
    }

    /** 启用商品（从 DISABLED 回到 DRAFT；ON_SALE/OFF_SALE 的上下架归 REQ-M2-003）。 */
    public void enable() {
        if (this.status == ProductStatus.DISABLED) {
            this.status = ProductStatus.DRAFT;
        }
    }

    /** 上架：校验通过后 DRAFT/OFF_SALE → ON_SALE，并注册 ProductPublished 事件。 */
    public void publish() {
        if (this.status == ProductStatus.DISABLED) {
            throw ProductException.publishValidationFailed("商品已禁用，不可上架");
        }
        if (this.status == ProductStatus.ON_SALE) {
            throw ProductException.alreadyOnSale();
        }
        if (!hasMainImage()) {
            throw ProductException.publishValidationFailed("缺少主图");
        }
        boolean hasEnabledSku = skus.stream().anyMatch(s -> s.getStatus() == SkuStatus.ENABLED);
        if (!hasEnabledSku) {
            throw ProductException.publishValidationFailed("至少需要一个启用状态的 SKU");
        }
        boolean invalidPrice = skus.stream()
                .filter(s -> s.getStatus() == SkuStatus.ENABLED)
                .anyMatch(s -> s.getSalePrice().amountInCents() < 0);
        if (invalidPrice) {
            throw ProductException.publishValidationFailed("存在价格非法的 SKU");
        }
        this.status = ProductStatus.ON_SALE;
        domainEvents.add(new ProductPublishedDomainEvent(this.id));
    }

    /** 下架：ON_SALE → OFF_SALE，并注册 ProductUnpublished 事件。 */
    public void unpublish() {
        if (this.status != ProductStatus.ON_SALE) {
            throw ProductException.notOnSale();
        }
        this.status = ProductStatus.OFF_SALE;
        domainEvents.add(new ProductUnpublishedDomainEvent(this.id));
    }

    public List<ProductDomainEvent> getDomainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    public boolean isDisabled() {
        return status == ProductStatus.DISABLED;
    }

    /** 新增 SKU：调用方需保证 skuCode 全局唯一。 */
    public void addSku(Sku sku) {
        if (skus.stream().anyMatch(s -> s.getSpecificationHash().value().equals(sku.getSpecificationHash().value()))) {
            throw ProductException.skuSpecDuplicated();
        }
        skus.add(sku);
    }

    /** 更新 SKU 价格与主图。 */
    public void updateSku(long skuId, long salePriceInCents, String mainImageUrl) {
        Sku sku = findSku(skuId);
        sku.updatePrice(salePriceInCents);
        sku.updateMainImage(mainImageUrl);
    }

    public void enableSku(long skuId) {
        findSku(skuId).enable();
    }

    public void disableSku(long skuId) {
        findSku(skuId).disable();
    }

    private Sku findSku(long skuId) {
        return skus.stream()
                .filter(s -> s.getId() == skuId)
                .findFirst()
                .orElseThrow(() -> ProductException.skuNotFound(skuId));
    }

    public boolean hasMainImage() {
        return mainImageUrl != null && !mainImageUrl.isBlank();
    }

    private void recomputeMainImage() {
        this.mainImageUrl = images.stream()
                .filter(ProductImage::mainFlag)
                .findFirst()
                .map(ProductImage::imageUrl)
                .orElse(null);
    }

    private static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("商品编码不能为空");
        }
        String code = raw.trim();
        if (code.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("商品编码最长 " + MAX_CODE_LENGTH + " 字符");
        }
        return code;
    }

    private static String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        String name = raw.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("商品名称最长 " + MAX_NAME_LENGTH + " 字符");
        }
        return name;
    }

    private static String normalizeSubtitle(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.length() > MAX_SUBTITLE_LENGTH) {
            throw new IllegalArgumentException("商品副标题最长 " + MAX_SUBTITLE_LENGTH + " 字符");
        }
        return s;
    }

    private static String normalizeDescription(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        return s.isEmpty() ? null : s;
    }

    public long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSubtitle() { return subtitle; }
    public String getDescription() { return description; }
    public long getCategoryId() { return categoryId; }
    public long getBrandId() { return brandId; }
    public ProductStatus getStatus() { return status; }
    public String getMainImageUrl() { return mainImageUrl; }
    public List<ProductImage> getImages() { return images; }
    public List<ProductAttribute> getAttributes() { return attributes; }
    public List<Sku> getSkus() { return skus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void assignCreated(long id, Instant now) {
        this.id = id;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }
}
