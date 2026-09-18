# DU Task Design — DU-BE-501

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。
> 本 DU 对应 STORY-005-01-01-01（ES 接入基线）；异常归一拆分至 DU-BE-510。

## 1. Goal

mall-search 接入官方 elasticsearch-java：Client 配置（超时/URI 可覆盖）、健康指标、Testcontainers 测试基线。

## 2. Repository

repo-1（ai-platform-backend：mall-services/mall-search；版本由 mall-bom/Spring Boot BOM 管理）

## 3. Scope

- mall-search/pom.xml：co.elastic.clients:elasticsearch-java、org.elasticsearch.client:elasticsearch-rest-client、jakarta.json-api + parsson 运行时；不写版本。
- application.yml：mall.elasticsearch.uris（${MALL_ES_URIS:http://localhost:9200}）、connect-timeout=2s、socket-timeout=5s、mall.search.index-alias=mall_products。
- infrastructure.elasticsearch.ElasticsearchConfiguration：RestClient + JacksonJsonpMapper + ElasticsearchClient Bean（构造不连接）。
- infrastructure.health.ElasticsearchHealthIndicator：ping→UP/DOWN。
- src/test：AbstractElasticsearchIT（static ElasticsearchContainer 8.17.4 withSecurityDisabled）、ElasticsearchSmokeIT、ContextLoadsTest。

## 4. Design References

- CHG-0020 requirement-design.md §2.0（技术选型）；STORY-005-01-01-01 story-design.md §1/§2/§4。

## 5. Dependencies

权威表：DU-WS-501（compose ES，运行/集成需要）。

## 6. Implementation Sketch

```
MallSearchApplication
 └─ ElasticsearchConfiguration ── RestClient(uris, timeouts)
        └─ ElasticsearchClient(JacksonJsonpMapper)
 health 端点 ── ElasticsearchHealthIndicator.ping()
```

- Client Bean 懒连接保证离线启动与 ContextLoadsTest。

## 7. Pseudocode

N/A（Bean 装配与 ping 调用均为直接 API 组合，无复杂分支；异常归一在 DU-BE-510）。
