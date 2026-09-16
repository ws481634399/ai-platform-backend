package com.ai.mall.product.domain.product;

import com.ai.mall.product.domain.product.ProductRepository.ProductPageResult;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 商品仓储端口。
 */
public interface ProductRepository {

    Optional<Product> findById(long id);

    /**
     * CHG-0018 DU-BE-801：按一批 SKU id 反查所属商品（聚合根完整 hydrated）。
     * 一次 IN 查询 + 每聚合根批量装载，禁止 N+1；只返回未删除且含命中 SKU 的商品。
     */
    List<Product> findBySkuIds(Collection<Long> skuIds);

    boolean existsByCode(String code, Long excludeId);

    boolean existsBySkuCode(String skuCode, Long excludeSkuId);

    boolean existsBySkuId(long skuId);

    ProductPageResult page(ProductPageQuery query);

    ProductPageResult mallPage(ProductPageQuery query);

    void insert(Product product);

    boolean update(Product product);

    record ProductPageQuery(String keyword, Long categoryId, Long brandId, String status, int page, int size,
                            List<Long> brandIds, List<Long> categoryIds, MallProductSort sort) {
        public ProductPageQuery(String keyword, Long categoryId, Long brandId, String status, int page, int size) {
            this(keyword, categoryId, brandId, status, page, size, null, null, MallProductSort.DEFAULT);
        }
    }

    /** 商城列表价区（整数分）：仅统计启用 SKU。 */
    record PriceRange(long minPrice, long maxPrice) {}

    /**
     * 分页结果。
     *
     * @param priceRanges 商城列表价区：productId -> 价区；管理端分页传空 Map
     */
    record ProductPageResult(List<Product> records, long total, int page, int size,
                             Map<Long, PriceRange> priceRanges) {

        /** 管理端分页：无价区。 */
        public ProductPageResult(List<Product> records, long total, int page, int size) {
            this(records, total, page, size, Map.of());
        }
    }
}
