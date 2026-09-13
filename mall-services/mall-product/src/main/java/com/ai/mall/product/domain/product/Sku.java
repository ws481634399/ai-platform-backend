package com.ai.mall.product.domain.product;

import java.time.Instant;
import java.util.List;

/**
 * SKU 实体（Product 聚合内）：编码、规格组合、销售价（分）、主图、状态。
 */
public class Sku {

    private static final int MAX_CODE_LENGTH = 64;

    private long id;
    private String code;
    private List<Specification> specifications;
    private SpecificationHash specificationHash;
    private Money salePrice;
    private SkuStatus status;
    private String mainImageUrl;
    private Instant createdAt;
    private Instant updatedAt;

    private Sku(long id, String code, List<Specification> specifications, SpecificationHash specificationHash,
                Money salePrice, SkuStatus status, String mainImageUrl, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.specifications = specifications;
        this.specificationHash = specificationHash;
        this.salePrice = salePrice;
        this.status = status;
        this.mainImageUrl = mainImageUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 新建 SKU，默认 ENABLED。 */
    public static Sku createNew(String code, List<Specification> specifications, long salePriceInCents, String mainImageUrl) {
        Money price = Money.ofCents(salePriceInCents);
        SpecificationHash hash = SpecificationHash.compute(specifications);
        return new Sku(0L, normalizeCode(code), List.copyOf(specifications), hash, price,
                SkuStatus.ENABLED, normalizeMainImage(mainImageUrl), null, null);
    }

    /** 持久化重建。 */
    public static Sku reconstitute(long id, String code, List<Specification> specifications, String specificationHash,
                                   long salePriceInCents, String status, String mainImageUrl,
                                   Instant createdAt, Instant updatedAt) {
        return new Sku(id, code, specifications, new SpecificationHash(specificationHash),
                Money.ofCents(salePriceInCents), SkuStatus.from(status), mainImageUrl, createdAt, updatedAt);
    }

    public void updatePrice(long salePriceInCents) {
        this.salePrice = Money.ofCents(salePriceInCents);
    }

    public void updateMainImage(String mainImageUrl) {
        this.mainImageUrl = normalizeMainImage(mainImageUrl);
    }

    public void enable() {
        this.status = SkuStatus.ENABLED;
    }

    public void disable() {
        this.status = SkuStatus.DISABLED;
    }

    private static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("SKU 编码不能为空");
        }
        String code = raw.trim();
        if (code.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("SKU 编码最长 " + MAX_CODE_LENGTH + " 字符");
        }
        return code;
    }

    private static String normalizeMainImage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    public long getId() { return id; }
    public String getCode() { return code; }
    public List<Specification> getSpecifications() { return specifications; }
    public SpecificationHash getSpecificationHash() { return specificationHash; }
    public Money getSalePrice() { return salePrice; }
    public SkuStatus getStatus() { return status; }
    public String getMainImageUrl() { return mainImageUrl; }
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
