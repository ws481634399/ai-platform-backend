package com.ai.mall.product.domain.product;

import com.ai.mall.product.domain.product.ProductRepository.ProductPageResult;
import java.util.List;
import java.util.Optional;

/**
 * 商品仓储端口。
 */
public interface ProductRepository {

    Optional<Product> findById(long id);

    boolean existsByCode(String code, Long excludeId);

    boolean existsBySkuCode(String skuCode, Long excludeSkuId);

    ProductPageResult page(ProductPageQuery query);

    void insert(Product product);

    boolean update(Product product);

    record ProductPageQuery(String keyword, Long categoryId, Long brandId, String status, int page, int size) {}

    record ProductPageResult(List<Product> records, long total, int page, int size) {}
}
