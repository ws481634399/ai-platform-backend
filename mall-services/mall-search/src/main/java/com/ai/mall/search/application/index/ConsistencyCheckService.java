package com.ai.mall.search.application.index;

import com.ai.mall.search.application.index.projection.ProductProjectionView;
import com.ai.mall.search.application.index.projection.ProjectionPage;
import com.ai.mall.search.domain.index.SearchIndexPort;
import com.ai.mall.search.infrastructure.client.ProductProjectionClient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 一致性检查（CHG-0021 DU-BE-504）：product 在架全集 vs ES docId 全集求差集。
 *
 * <p>差异列表各截断 200（M5 数据量千级），counts 不受截断影响。
 */
@Service
public class ConsistencyCheckService {

    public static final int BATCH_SIZE = 500;
    public static final int DIFF_LIMIT = 200;

    private final ProductProjectionClient projectionClient;
    private final SearchIndexPort searchIndexPort;
    private final String alias;

    public ConsistencyCheckService(ProductProjectionClient projectionClient,
                                   SearchIndexPort searchIndexPort,
                                   @Value("${mall.search.index-alias:mall_products}") String alias) {
        this.projectionClient = projectionClient;
        this.searchIndexPort = searchIndexPort;
        this.alias = alias;
    }

    public Map<String, Object> check() {
        // 1. product 在架全集（分页 500）
        Set<Long> productIds = new LinkedHashSet<>();
        long onSaleCount = 0;
        int page = 1;
        while (true) {
            ProjectionPage projection = projectionClient.fetchPage(page, BATCH_SIZE);
            if (page == 1) {
                onSaleCount = projection.total();
            }
            List<ProductProjectionView> items = projection.items();
            if (items.isEmpty()) {
                break;
            }
            items.forEach(v -> productIds.add(Long.parseLong(v.productId())));
            if (items.size() < BATCH_SIZE || productIds.size() >= onSaleCount) {
                break;
            }
            page++;
        }

        // 2. ES 全集（search_after 分批）
        List<String> esIdStrings = searchIndexPort.allIds(alias, BATCH_SIZE);
        Set<Long> esIds = new LinkedHashSet<>();
        esIdStrings.forEach(id -> esIds.add(Long.parseLong(id)));
        long indexCount = searchIndexPort.count(alias);

        // 3. 差集（保持稳定顺序）
        List<Long> missing = productIds.stream().filter(id -> !esIds.contains(id)).toList();
        List<Long> extra = esIds.stream().filter(id -> !productIds.contains(id)).toList();
        boolean missingTruncated = missing.size() > DIFF_LIMIT;
        boolean extraTruncated = extra.size() > DIFF_LIMIT;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("productOnSaleCount", onSaleCount);
        report.put("indexCount", indexCount);
        report.put("missingProductIds", missing.stream().limit(DIFF_LIMIT).toList());
        report.put("extraProductIds", extra.stream().limit(DIFF_LIMIT).toList());
        report.put("missingTruncated", missingTruncated);
        report.put("extraTruncated", extraTruncated);
        report.put("checkedAt", Instant.now().toEpochMilli());
        return report;
    }
}
