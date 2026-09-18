package com.ai.mall.product.infrastructure.persistence.product;

import java.time.Instant;

/**
 * 搜索投影查询行（CHG-0021，只读）：ON_SALE 且存在 ENABLED SKU 口径，
 * 含分类/品牌名与启用 SKU 价格区间；keywords M5 固定空串列预留。
 * 非表映射 PO，仅承载 ProductSearchProjectionMapper 自定义 JOIN 查询结果
 * （列名经下划线转驼峰自动映射）。
 */
public class ProductSearchProjectionPo {

    private Long id;
    private String productName;
    private String keywords;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private String mainImage;
    private String status;
    private Long minPriceFen;
    private Long maxPriceFen;
    private Instant publishedAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public Long getBrandId() { return brandId; }
    public void setBrandId(Long brandId) { this.brandId = brandId; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getMainImage() { return mainImage; }
    public void setMainImage(String mainImage) { this.mainImage = mainImage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getMinPriceFen() { return minPriceFen; }
    public void setMinPriceFen(Long minPriceFen) { this.minPriceFen = minPriceFen; }
    public Long getMaxPriceFen() { return maxPriceFen; }
    public void setMaxPriceFen(Long maxPriceFen) { this.maxPriceFen = maxPriceFen; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
