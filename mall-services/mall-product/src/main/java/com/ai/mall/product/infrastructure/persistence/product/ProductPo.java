package com.ai.mall.product.infrastructure.persistence.product;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("product_spu")
public class ProductPo {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String productCode;
    private String productName;
    private String subtitle;
    private String description;
    private Long categoryId;
    private Long brandId;
    private String status;
    private Long minPrice;
    private Long maxPrice;
    private String mainImageUrl;
    private Long salesCount;
    private Instant publishedAt;
    private Instant unpublishedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Long createdBy;
    private Long updatedBy;
    private Long version;
    private Integer deleted;

    @TableField(exist = false)
    private java.util.List<ProductImagePo> images;
    @TableField(exist = false)
    private java.util.List<ProductAttributePo> attributes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getBrandId() { return brandId; }
    public void setBrandId(Long brandId) { this.brandId = brandId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getMinPrice() { return minPrice; }
    public void setMinPrice(Long minPrice) { this.minPrice = minPrice; }
    public Long getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Long maxPrice) { this.maxPrice = maxPrice; }
    public String getMainImageUrl() { return mainImageUrl; }
    public void setMainImageUrl(String mainImageUrl) { this.mainImageUrl = mainImageUrl; }
    public Long getSalesCount() { return salesCount; }
    public void setSalesCount(Long salesCount) { this.salesCount = salesCount; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getUnpublishedAt() { return unpublishedAt; }
    public void setUnpublishedAt(Instant unpublishedAt) { this.unpublishedAt = unpublishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public java.util.List<ProductImagePo> getImages() { return images; }
    public void setImages(java.util.List<ProductImagePo> images) { this.images = images; }
    public java.util.List<ProductAttributePo> getAttributes() { return attributes; }
    public void setAttributes(java.util.List<ProductAttributePo> attributes) { this.attributes = attributes; }
}
