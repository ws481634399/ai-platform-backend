package com.ai.mall.search.application.index;

import com.ai.mall.search.application.index.projection.ProductProjectionView;
import com.ai.mall.search.application.index.projection.ProjectionPage;
import com.ai.mall.search.domain.index.SearchIndexPort;
import com.ai.mall.search.infrastructure.client.ProductProjectionClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 全量索引构建（CHG-0021 DU-BE-503）：分页 500 拉 product 投影 → 批次 Bulk 写目标物理索引。
 * 目标索引可能是 v1（首次）或 rebuild 临时索引（重建），绝不直接写别名。
 */
@Service
public class FullIndexBuildService {

    public static final int PAGE_SIZE = 500;

    private static final Logger log = LoggerFactory.getLogger(FullIndexBuildService.class);

    private final ProductProjectionClient projectionClient;
    private final SearchIndexPort searchIndexPort;

    public FullIndexBuildService(ProductProjectionClient projectionClient,
                                 SearchIndexPort searchIndexPort) {
        this.projectionClient = projectionClient;
        this.searchIndexPort = searchIndexPort;
    }

    /** 批次进度回调（total/indexed）。 */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int total, int indexed);
    }

    /**
     * 全量构建。
     *
     * @return 写入文档总数
     */
    public int build(String targetIndex, ProgressCallback callback) {
        int page = 1;
        int indexed = 0;
        long total = -1;
        while (true) {
            ProjectionPage projection = projectionClient.fetchPage(page, PAGE_SIZE);
            List<ProductProjectionView> items = projection.items();
            if (total < 0) {
                total = projection.total();
                if (callback != null) {
                    callback.onProgress((int) total, 0);
                }
            }
            if (items.isEmpty()) {
                break;
            }
            searchIndexPort.bulkUpsert(targetIndex, items);
            indexed += items.size();
            if (callback != null) {
                callback.onProgress((int) total, indexed);
            }
            log.info("全量构建批次完成 index={} page={} batch={} indexed={}", targetIndex, page, items.size(), indexed);
            if (items.size() < PAGE_SIZE || indexed >= total) {
                break;
            }
            page++;
        }
        return indexed;
    }
}
