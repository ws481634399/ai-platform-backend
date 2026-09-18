package com.ai.mall.product.application.search;

import com.ai.mall.product.infrastructure.persistence.product.ProductSearchProjectionMapper;
import com.ai.mall.product.infrastructure.persistence.product.ProductSearchProjectionPo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 搜索投影查询应用服务（CHG-0021）：供内部端点与 AFTER_COMMIT 同步监听器复用，
 * 单条查询 null 语义 = 已删除/非在架/无启用 SKU（调用方走删除文档分支）。
 */
@Service
public class ProductSearchProjectionService {

    private final ProductSearchProjectionMapper mapper;

    public ProductSearchProjectionService(ProductSearchProjectionMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public SearchProjectionPage page(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 500));
        Page<ProductSearchProjectionPo> result =
                mapper.selectProjectionPage(new Page<>(safePage, safeSize));
        List<SearchProjectionView> items = result.getRecords().stream()
                .map(ProductSearchProjectionService::toView).toList();
        return new SearchProjectionPage(items, result.getTotal(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public SearchProjectionView findById(long productId) {
        ProductSearchProjectionPo po = mapper.selectProjectionById(productId);
        return po == null ? null : toView(po);
    }

    private static SearchProjectionView toView(ProductSearchProjectionPo po) {
        return new SearchProjectionView(
                String.valueOf(po.getId()),
                po.getProductName(),
                po.getKeywords() == null ? "" : po.getKeywords(),
                po.getCategoryId(),
                po.getCategoryName(),
                po.getBrandId(),
                po.getBrandName(),
                po.getMainImage(),
                po.getStatus(),
                po.getMinPriceFen(),
                po.getMaxPriceFen(),
                millis(po.getPublishedAt()),
                millis(po.getUpdatedAt()));
    }

    private static Long millis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
