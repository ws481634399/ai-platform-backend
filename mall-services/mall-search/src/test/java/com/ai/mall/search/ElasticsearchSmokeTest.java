package com.ai.mall.search;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import com.ai.mall.search.support.AbstractElasticsearchTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ES 冒烟集成测试（DU-BE-501 TC-005）：临时索引写入 2 文档并检索命中，结束自动清理。
 */
@SpringBootTest
@ActiveProfiles("test")
class ElasticsearchSmokeTest extends AbstractElasticsearchTest {

    @Autowired
    private ElasticsearchClient client;

    @Test
    void indexTwoDocumentsAndSearchHits() throws Exception {
        String index = "smoke-it-" + System.nanoTime();
        try {
            CreateIndexResponse created = client.indices().create(c -> c.index(index));
            assertThat(created.acknowledged()).isTrue();

            client.index(i -> i.index(index).id("1").document(new SmokeDoc("机械键盘", 19900L)));
            client.index(i -> i.index(index).id("2").document(new SmokeDoc("无线鼠标", 9900L)));
            client.indices().refresh(r -> r.index(index));

            var resp = client.search(s -> s.index(index).query(q -> q.match(m -> m.field("name").query("机械"))),
                    SmokeDoc.class);
            assertThat(resp.hits().total().value()).isEqualTo(1L);
            assertThat(resp.hits().hits()).hasSize(1);
            assertThat(resp.hits().hits().get(0).source().name()).isEqualTo("机械键盘");
        } finally {
            try {
                DeleteIndexResponse deleted = client.indices().delete(d -> d.index(index));
                assertThat(deleted.acknowledged()).isTrue();
            } catch (Exception ignored) {
                // 清理失败不影响结论
            }
        }
    }

    /** 最小 JSON 文档映射夹具。 */
    public record SmokeDoc(String name, Long priceFen) {
    }
}

