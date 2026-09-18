package com.ai.mall.search.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.ai.mall.search.domain.search.ProductSearchItem;
import com.ai.mall.search.domain.search.ProductSearchPort;
import com.ai.mall.search.domain.search.SearchPage;
import com.ai.mall.search.domain.search.SearchProductDocument;
import com.ai.mall.search.domain.search.SearchQuery;
import com.ai.mall.search.domain.search.SortMode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 商品搜索 ES 出站适配器（CHG-0020 DU-BE-502/511）。
 *
 * <p>bool：keyword 非空时 must multi_match（productName^3/keywords/brandName/categoryName，or）；
 * filter 恒含 status=ON_SALE，叠加 categoryId/brandId term 与价格区间相交 range；
 * from/size 分页，hits.total.value 为 total；ES 抛错由 SearchExceptionAdvice 统一归一。
 */
@Component
public class ElasticsearchProductSearchAdapter implements ProductSearchPort {

    private final ElasticsearchClient client;
    private final String indexAlias;

    public ElasticsearchProductSearchAdapter(ElasticsearchClient client,
                                           @Value("${mall.search.index-alias:mall_products}") String indexAlias) {
        this.client = client;
        this.indexAlias = indexAlias;
    }

    @Override
    public SearchPage<ProductSearchItem> search(SearchQuery query) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (query.keyword() != null) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(query.keyword())
                    .fields("productName^3", "keywords", "brandName", "categoryName")
                    .operator(Operator.Or)));
        }
        bool.filter(f -> f.term(t -> t.field("status")
                .value(FieldValue.of(SearchProductDocument.STATUS_ON_SALE))));
        if (query.categoryId() != null) {
            bool.filter(f -> f.term(t -> t.field("categoryId").value(FieldValue.of(query.categoryId()))));
        }
        if (query.brandId() != null) {
            bool.filter(f -> f.term(t -> t.field("brandId").value(FieldValue.of(query.brandId()))));
        }
        // 区间相交：给出上限时要求 doc.minPrice <= 上限；给出下限时要求 doc.maxPrice >= 下限
        // （单侧开边界；均未给出时不加 range，避免向 long 字段发送越界 double）
        if (query.maxPriceFen() != null) {
            bool.filter(f -> f.range(r -> r.number(n -> n.field("minPrice")
                    .lte(query.maxPriceFen().doubleValue()))));
        }
        if (query.minPriceFen() != null) {
            bool.filter(f -> f.range(r -> r.number(n -> n.field("maxPrice")
                    .gte(query.minPriceFen().doubleValue()))));
        }

        SearchResponse<SearchProductDocument> response;
        try {
            response = client.search(s -> s
                            .index(indexAlias)
                            .query(q -> q.bool(bool.build()))
                            .sort(sorts(query.sort()))
                            .from((query.page() - 1) * query.size())
                            .size(query.size())
                            .trackTotalHits(t -> t.enabled(true)),
                    SearchProductDocument.class);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        List<ProductSearchItem> items = new ArrayList<>(response.hits().hits().size());
        for (var hit : response.hits().hits()) {
            SearchProductDocument d = hit.source();
            if (d == null) {
                continue;
            }
            items.add(new ProductSearchItem(d.productId(), d.productName(), d.mainImage(),
                    d.minPrice(), d.maxPrice(), d.brandName(), d.categoryName()));
        }
        long total = response.hits().total() == null ? 0L : response.hits().total().value();
        return new SearchPage<>(items, total, query.page(), query.size());
    }

    private List<SortOptions> sorts(SortMode mode) {
        List<SortOptions> options = new ArrayList<>(2);
        switch (mode) {
            case PRICE_ASC -> {
                options.add(fieldSort("minPrice", SortOrder.Asc, null));
                options.add(scoreSort());
            }
            case PRICE_DESC -> {
                options.add(fieldSort("maxPrice", SortOrder.Desc, null));
                options.add(scoreSort());
            }
            case NEWEST -> {
                // publishedAt 缺失排尾，再以 updatedAt/_score 稳定兜底由 score 次级给出
                options.add(fieldSort("publishedAt", SortOrder.Desc, "_last"));
                options.add(scoreSort());
            }
            case DEFAULT -> {
                options.add(scoreSort());
                options.add(fieldSort("updatedAt", SortOrder.Desc, null));
            }
        }
        return options;
    }

    private static SortOptions fieldSort(String field, SortOrder order, String missing) {
        return SortOptions.of(s -> s.field(f -> {
            f.field(field).order(order);
            if (missing != null) {
                f.missing(missing);
            }
            return f;
        }));
    }

    private static SortOptions scoreSort() {
        return SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)));
    }
}
