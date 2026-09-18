package com.ai.mall.search.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import java.time.Duration;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 官方 Java Client 接入（CHG-0020 DU-BE-501）。
 *
 * <p>版本继承 Spring Boot BOM；Client 构造期不发起连接，ES 不可达不阻止应用启动。
 * URI/超时由 mall.elasticsearch.* 配置，支持 MALL_ES_URIS 环境变量覆盖。
 */
@Configuration
@ConfigurationProperties(prefix = "mall.elasticsearch")
public class ElasticsearchConfiguration {

    /** ES 地址列表，逗号分隔（默认 http://localhost:9200）。 */
    private String uris = "http://localhost:9200";

    /** 建连超时。 */
    private Duration connectTimeout = Duration.ofSeconds(2);

    /** Socket/读超时。 */
    private Duration socketTimeout = Duration.ofSeconds(5);

    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient() {
        String[] uriArray = uris.split(",");
        HttpHost[] hosts = new HttpHost[uriArray.length];
        for (int i = 0; i < uriArray.length; i++) {
            hosts[i] = HttpHost.create(uriArray[i].trim());
        }
        RestClientBuilder builder = RestClient.builder(hosts);
        int connectMs = (int) connectTimeout.toMillis();
        int socketMs = (int) socketTimeout.toMillis();
        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(connectMs)
                .setSocketTimeout(socketMs));
        return builder.build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    public String getUris() {
        return uris;
    }

    public void setUris(String uris) {
        this.uris = uris;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(Duration socketTimeout) {
        this.socketTimeout = socketTimeout;
    }
}
