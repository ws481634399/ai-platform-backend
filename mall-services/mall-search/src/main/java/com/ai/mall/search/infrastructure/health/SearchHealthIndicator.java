package com.ai.mall.search.infrastructure.health;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * mall-search 对 Elasticsearch 的健康指标（CHG-0020 DU-BE-501）。
 *
 * <p>ping 成功 → UP；任何异常 → DOWN（异常不外抛，不影响进程存活）。
 * actuator 暴露路径：/actuator/health，组件名 components.search。
 */
@Component("searchHealthIndicator")
public class SearchHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(SearchHealthIndicator.class);

    private final ElasticsearchClient elasticsearchClient;

    public SearchHealthIndicator(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public Health health() {
        try {
            BooleanResponse response = elasticsearchClient.ping();
            if (response.value()) {
                return Health.up().withDetail("elasticsearch", "UP").build();
            }
            return Health.down().withDetail("elasticsearch", "ping returned false").build();
        } catch (Exception ex) {
            log.warn("Elasticsearch 健康检查 DOWN: {}", ex.getMessage());
            return Health.down(ex).withDetail("elasticsearch", "DOWN").build();
        }
    }
}
