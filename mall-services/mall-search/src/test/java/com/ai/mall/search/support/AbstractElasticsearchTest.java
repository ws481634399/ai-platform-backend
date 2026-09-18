package com.ai.mall.search.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 真实 Elasticsearch Testcontainers 基类（CHG-0020 DU-BE-501）。
 *
 * <p>每 JVM 启动一个单节点 ES 8.17.4（关闭 xpack security，与 compose 形态一致），
 * 供冒烟/查询/同步集成测试复用；Client 经 mall.elasticsearch.uris 动态注入。
 */
@Testcontainers
public abstract class AbstractElasticsearchTest {

    static {
        // 双保险：环境不具备 Ryuk 镜像时避免阻塞容器启动（surefire 已注入同名环境变量）
        if (System.getProperty("testcontainers.ryuk.disabled") == null) {
            System.setProperty("testcontainers.ryuk.disabled", "true");
        }
    }

    @SuppressWarnings("resource")
    protected static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer(
                            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.17.4"))
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("xpack.security.enrollment.enabled", "false")
                    .withEnv("discovery.type", "single-node")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                    .withReuse(false);

    static {
        ELASTICSEARCH.start();
    }

    @DynamicPropertySource
    static void registerElasticsearch(DynamicPropertyRegistry registry) {
        registry.add("mall.elasticsearch.uris", ELASTICSEARCH::getHttpHostAddress);
    }
}

