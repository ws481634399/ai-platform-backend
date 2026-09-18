package com.ai.mall.search.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import com.ai.mall.search.application.index.projection.ProductProjectionView;
import com.ai.mall.search.domain.search.SearchProductDocument;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * ES 索引出站适配器（CHG-0021）。
 *
 * <p>冻结映射使用 IK 分词器（resources/es/product-index.json）；运行环境镜像未装
 * analysis-ik 插件时（本地 compose/Testcontainers 官方镜像）回退 standard 映射
 * （product-index-standard.json），字段类型与冻结版本完全一致，仅分词器不同，
 * 生产带插件镜像自动使用冻结版本。
 */
@Component
public class EsSearchIndexAdapter implements com.ai.mall.search.domain.index.SearchIndexPort {

    private static final Logger log = LoggerFactory.getLogger(EsSearchIndexAdapter.class);

    private final ElasticsearchClient client;
    private final String alias;

    public EsSearchIndexAdapter(ElasticsearchClient client,
                               @Value("${mall.search.index-alias:mall_products}") String alias) {
        this.client = client;
        this.alias = alias;
    }

    @Override
    public void createProductIndex(String physicalIndex) {
        try {
            createWithResource(physicalIndex, "es/product-index.json");
        } catch (Exception primary) {
            if (isMissingIkAnalyzer(primary)) {
                log.warn("ES 镜像未安装 analysis-ik 插件，索引 {} 回退 standard 分词映射（字段类型不变）", physicalIndex);
                createWithResource(physicalIndex, "es/product-index-standard.json");
            } else {
                throw primary;
            }
        }
    }

    private void createWithResource(String physicalIndex, String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            client.indices().create(c -> c.index(physicalIndex).withJson(in));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static boolean isMissingIkAnalyzer(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && (message.contains("ik_max_word") || message.contains("analyzer [ik"))) {
                return true;
            }
            if (t == t.getCause()) {
                break;
            }
        }
        return false;
    }

    @Override
    public boolean indexExists(String physicalIndex) {
        try {
            return client.indices().exists(e -> e.index(physicalIndex)).value();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public Set<String> physicalIndicesOf(String aliasName) {
        try {
            boolean aliasExists = client.indices().existsAlias(e -> e.name(aliasName)).value();
            if (!aliasExists) {
                return Set.of();
            }
            GetAliasResponse response = client.indices().getAlias(g -> g.name(aliasName));
            return new LinkedHashSet<>(response.result().keySet());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void putAlias(String physicalIndex, String aliasName) {
        try {
            client.indices().putAlias(p -> p.index(physicalIndex).name(aliasName));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void switchAlias(String aliasName, Set<String> oldIndices, String newIndex) {
        try {
            client.indices().updateAliases(u -> u.actions(actions -> {
                for (String old : oldIndices) {
                    actions.remove(r -> r.index(old).alias(aliasName));
                }
                actions.add(a -> a.index(newIndex).alias(aliasName));
                return actions;
            }));
            // 全量构建批次 bulk refresh=false（NRT）；切换后显式 refresh，
            // 保证重建完成即刻 count/查询一致，不依赖 1s 默认刷新间隔
            client.indices().refresh(r -> r.index(newIndex));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void deleteIndices(Set<String> indices) {
        if (indices == null || indices.isEmpty()) {
            return;
        }
        try {
            client.indices().delete(d -> d.index(new ArrayList<>(indices)));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void bulkUpsert(String physicalIndex, List<ProductProjectionView> views) {
        if (views == null || views.isEmpty()) {
            return;
        }
        List<BulkOperation> operations = views.stream()
                .map(view -> BulkOperation.of(op -> op.index(i -> i
                        .index(physicalIndex)
                        .id(view.productId())
                        .version(view.updatedAt())
                        .versionType(VersionType.ExternalGte)
                        .document(toDocument(view)))))
                .toList();
        BulkResponse response;
        try {
            response = client.bulk(b -> b.index(physicalIndex).operations(operations).refresh(Refresh.False));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (response.errors()) {
            String detail = response.items().stream()
                    .filter(item -> item.error() != null)
                    .map(item -> item.id() + ":" + item.error().reason())
                    .findFirst().orElse("bulk errors");
            throw new IllegalStateException("批量写入失败: " + detail);
        }
    }

    @Override
    public IndexWriteResult upsertVersioned(ProductProjectionView view) {
        try {
            client.index(i -> i.index(alias)
                    .id(view.productId())
                    .version(view.updatedAt())
                    .versionType(VersionType.ExternalGte)
                    .document(toDocument(view))
                    .refresh(Refresh.True));
            return IndexWriteResult.WRITTEN;
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException ex) {
            if (ex.status() == 409) {
                return IndexWriteResult.STALE_VERSION;
            }
            throw ex;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public IndexWriteResult deleteVersioned(ProductProjectionView view) {
        try {
            client.delete(d -> d.index(alias)
                    .id(view.productId())
                    .version(view.updatedAt())
                    .versionType(VersionType.ExternalGte)
                    .refresh(Refresh.True));
            return IndexWriteResult.WRITTEN;
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException ex) {
            if (ex.status() == 409) {
                return IndexWriteResult.STALE_VERSION;
            }
            if (ex.status() == 404) {
                // 文档已不存在：删除目标已达成
                return IndexWriteResult.WRITTEN;
            }
            throw ex;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void deletePlain(long productId) {
        try {
            client.delete(d -> d.index(alias).id(String.valueOf(productId)).refresh(Refresh.True));
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException ex) {
            if (ex.status() == 404) {
                return;
            }
            throw ex;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public long count(String aliasName) {
        try {
            return client.count(c -> c.index(aliasName)).count();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public List<String> allIds(String aliasName, int batchSize) {
        List<String> ids = new ArrayList<>();
        // ES 8 禁止按 _id 排序（fielddata 默认禁用）：全量 dump 使用 PIT + _shard_doc 游标
        String pitId;
        try {
            pitId = client.openPointInTime(p -> p.index(aliasName)
                    .keepAlive(Time.of(t -> t.time("1m")))).id();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        List<FieldValue> searchAfter = List.of();
        try {
            boolean hasMore = true;
            while (hasMore) {
                List<FieldValue> cursor = searchAfter;
                String currentPit = pitId;
                SearchResponse<Void> response = client.search(s -> {
                    s.pit(p -> p.id(currentPit).keepAlive(Time.of(t -> t.time("1m"))))
                            .size(batchSize)
                            .source(src -> src.fetch(false))
                            .trackTotalHits(t -> t.enabled(true))
                            .sort(so -> so.field(f -> f.field("_shard_doc")
                                    .order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)));
                    if (!cursor.isEmpty()) {
                        s.searchAfter(cursor);
                    }
                    return s;
                }, Void.class);
                var hits = response.hits().hits();
                for (var hit : hits) {
                    ids.add(hit.id());
                }
                if (hits.size() < batchSize) {
                    hasMore = false;
                } else {
                    searchAfter = hits.get(hits.size() - 1).sort() == null
                            ? List.of() : hits.get(hits.size() - 1).sort();
                    if (searchAfter.isEmpty()) {
                        hasMore = false;
                    }
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            try {
                client.closePointInTime(c -> c.id(pitId));
            } catch (IOException closeEx) {
                // PIT 有 1 分钟 keepAlive 兜底，关闭失败仅记录
                log.warn("关闭 PIT 失败 pitId={}", pitId, closeEx);
            }
        }
        return ids;
    }

    private static SearchProductDocument toDocument(ProductProjectionView view) {
        return new SearchProductDocument(
                Long.parseLong(view.productId()),
                view.productName(),
                view.keywords() == null ? "" : view.keywords(),
                view.categoryId(),
                view.categoryName(),
                view.brandId(),
                view.brandName(),
                view.mainImage(),
                view.status(),
                view.minPriceFen(),
                view.maxPriceFen(),
                view.publishedAt(),
                view.updatedAt());
    }
}
