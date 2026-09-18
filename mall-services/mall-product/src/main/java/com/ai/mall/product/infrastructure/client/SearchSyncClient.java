package com.ai.mall.product.infrastructure.client;

import com.ai.mall.common.security.InternalIdentityFilter;
import com.ai.mall.product.application.search.SearchProjectionView;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * mall-search 同步客户端（CHG-0021）：直连 8107 内部端点 + X-Internal-Token。
 *
 * <p>仅在 AFTER_COMMIT 监听器中调用，连接 1s/读 3s 快速失败；
 * search 端契约为"故障受理 200 + 失败落表"，因此只有网络层异常会抛出，由监听器兜底记录日志。
 */
@Component
public class SearchSyncClient {

    private final RestClient restClient;

    public SearchSyncClient(
            @Value("${mall.product.search-service-uri:http://localhost:8107}") String searchServiceUri,
            @Value("${mall.security.internal.shared-secret:dev-internal-secret}") String sharedSecret) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder()
                .baseUrl(searchServiceUri)
                .requestFactory(requestFactory)
                .defaultHeader(InternalIdentityFilter.INTERNAL_TOKEN_HEADER, sharedSecret)
                .build();
    }

    /** 投影同步（upsert/状态变更当前态），search 端恒返 accepted:true。 */
    public void sync(SearchProjectionView view) {
        restClient.post()
                .uri("/api/internal/search/products/sync")
                .body(view)
                .retrieve()
                .toBodilessEntity();
    }

    /** 硬删除文档（下架/删除/不可见）。 */
    public void delete(long productId) {
        restClient.delete()
                .uri("/api/internal/search/products/{productId}", productId)
                .retrieve()
                .toBodilessEntity();
    }
}
