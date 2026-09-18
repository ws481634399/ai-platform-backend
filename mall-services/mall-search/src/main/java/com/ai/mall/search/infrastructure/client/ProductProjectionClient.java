package com.ai.mall.search.infrastructure.client;

import com.ai.mall.search.application.index.projection.ProductProjectionView;
import com.ai.mall.search.application.index.projection.ProjectionPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * mall-product 投影端点客户端（CHG-0021）：直连 8103 + X-Internal-Token。
 *
 * <p>不引入 OpenFeign：M5 仅两个 GET 端点，Spring RestClient 足够；
 * 单条投影 404 语义为"已删除/不可见"，返回 null 由调用方走删除分支。
 */
@Component
public class ProductProjectionClient {

    private static final Logger log = LoggerFactory.getLogger(ProductProjectionClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ProductProjectionClient(
            @Value("${mall.sync.product-uri:http://localhost:8103}") String productBaseUri,
            @Value("${mall.security.internal.shared-secret:dev-internal-secret}") String internalToken,
            ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(productBaseUri)
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
        this.objectMapper = objectMapper;
    }

    /** 分页拉取在架商品投影（page 从 1 起）。 */
    public ProjectionPage fetchPage(int page, int size) {
        String body = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/internal/products/search-projection")
                        .queryParam("page", page).queryParam("size", size).build())
                .retrieve().body(String.class);
        return parsePage(body);
    }

    /** 单条投影；404（不存在/非在架）返回 null。 */
    public ProductProjectionView fetchOne(long productId) {
        try {
            String body = restClient.get()
                    .uri("/api/internal/products/{id}/search-projection", productId)
                    .retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("success").asBoolean(false)) {
                throw new IllegalStateException("投影端点返回失败: " + root.path("code").asText());
            }
            JsonNode data = root.path("data");
            return data.isMissingNode() || data.isNull() ? null
                    : objectMapper.treeToValue(data, ProductProjectionView.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatusCode.valueOf(404)) {
                return null;
            }
            throw new IllegalStateException("拉取商品投影失败 productId=" + productId
                    + " status=" + ex.getStatusCode().value(), ex);
        } catch (IOException ex) {
            throw new IllegalStateException("解析商品投影失败 productId=" + productId, ex);
        }
    }

    private ProjectionPage parsePage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("success").asBoolean(false)) {
                throw new IllegalStateException("投影端点返回失败: " + root.path("code").asText());
            }
            JsonNode data = root.path("data");
            long total = data.path("total").asLong(0);
            int page = data.path("page").asInt(1);
            int size = data.path("size").asInt(0);
            CollectionType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, ProductProjectionView.class);
            JsonNode itemsNode = data.path("items");
            List<ProductProjectionView> items = itemsNode.isMissingNode() || itemsNode.isNull()
                    ? List.of() : objectMapper.convertValue(itemsNode, listType);
            return new ProjectionPage(items, total, page, size);
        } catch (IOException ex) {
            throw new IllegalStateException("解析商品投影分页失败", ex);
        }
    }
}
