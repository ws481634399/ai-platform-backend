package com.ai.mall.product.infrastructure.persistence.product;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("product_sku")
public class SkuPo {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long productId;
    private String skuCode;
    private Long salePrice;
    private String status;
    private String mainImageUrl;
    /** 规格 JSON 字符串，列名 specification_data 避免 MyBatis-Plus 自动套用 JacksonTypeHandler。 */
    @TableField("specification_data")
    private String specificationContent;
    private String specificationHash;
    private Instant createdAt;
    private Instant updatedAt;
    private Long createdBy;
    private Long updatedBy;
    private Long version;
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public Long getSalePrice() { return salePrice; }
    public void setSalePrice(Long salePrice) { this.salePrice = salePrice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMainImageUrl() { return mainImageUrl; }
    public void setMainImageUrl(String mainImageUrl) { this.mainImageUrl = mainImageUrl; }
    public String getSpecificationContent() { return specificationContent; }
    public void setSpecificationContent(String specificationContent) { this.specificationContent = specificationContent; }
    public String getSpecificationHash() { return specificationHash; }
    public void setSpecificationHash(String specificationHash) { this.specificationHash = specificationHash; }
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
}
