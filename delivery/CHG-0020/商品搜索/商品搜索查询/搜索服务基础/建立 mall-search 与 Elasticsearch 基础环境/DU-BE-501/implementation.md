# DU Implementation — DU-BE-501

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

### mall-services/mall-search：官方 elasticsearch-java 接入（8107）

- `mall-services/mall-search/pom.xml`：
  - 新增 `co.elastic.clients:elasticsearch-java`、`org.elasticsearch.client:elasticsearch-rest-client`（均不写 `<version>`，由 Spring Boot 3.5.15 BOM 托管，本地依赖解析为 8.18.8）；
  - JSON-P 运行时 `jakarta.json:jakarta.json-api` + `org.eclipse.parsson:parsson`（runtime）；
  - 新增 `spring-boot-starter-actuator`（健康指标）；test 新增 `org.testcontainers:testcontainers/junit-jupiter/elasticsearch`；surefire 注入 `TESTCONTAINERS_RYUK_DISABLED=true`。
- `src/main/resources/application.yml`：`mall.elasticsearch.uris=${MALL_ES_URIS:http://localhost:9200}`、`connect-timeout: 2s`、`socket-timeout: 5s`、`mall.search.index-alias: mall_products`；`management.endpoints.web.exposure.include: health`、`management.health.elasticsearch.enabled: false`；日志 pattern 含 `%X{traceId:-}`。
- `infrastructure/elasticsearch/ElasticsearchConfiguration.java`：`@ConfigurationProperties(prefix="mall.elasticsearch")`（uris 逗号分隔/connectTimeout 2s/socketTimeout 5s）；Bean `elasticsearchRestClient`（HttpHost 数组 + RequestConfig 超时，destroyMethod=close）与 `elasticsearchClient`（RestClientTransport + JacksonJsonpMapper）；构造期不发起连接，ES 离线不阻止启动。
- `infrastructure/health/SearchHealthIndicator.java`：`@Component("searchHealthIndicator")` implements HealthIndicator → actuator 组件名 `components.search`；`client.ping()` true→UP，任何异常→DOWN（warn 日志、Health.down(ex)，不外抛、不影响进程存活）。
- `MallSearchApplication.java`：@SpringBootApplication + @ConfigurationPropertiesScan（@EnableScheduling 为后续 CHG-0021 同步任务预留）。

### 测试基线（Testcontainers ES 8.17.4）

- `src/test/java/com/ai/mall/search/support/AbstractElasticsearchTest.java`：static 单例 `ElasticsearchContainer`（镜像 `docker.elastic.co/elasticsearch/elasticsearch:8.17.4`，withEnv 关 security / single-node / 512m 堆，withReuse(false)），双保险关闭 Ryuk；`@DynamicPropertySource` 把容器 httpHostAddress 注入 `mall.elasticsearch.uris`。
- `src/test/java/com/ai/mall/search/ElasticsearchSmokeTest.java`：`indexTwoDocumentsAndSearchHits`——建临时索引 `smoke-it-<nanoTime>` → index 2 文档（机械键盘/无线鼠标）→ refresh → match「机械」命中 1 条，finally 删除临时索引。
- `src/test/java/com/ai/mall/search/MallSearchApplicationSmokeTest.java`：`contextLoads`（test profile H2 内存库承载数据源，无 ES 容器亦可加载，证明 Client Bean 懒连接）。

## Commits

| Commit | DU | 说明 |
| --- | --- | --- |
| 82ccf6e | DU-BE-501 | repo-1（M5 三 Change 合并提交）：mall-search ES 官方 Client 接入/配置/健康指标 + Testcontainers 8.17.4 基线与冒烟 |

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
               - 原 DU 建议:
               - 实际实现:
               - 原因:
               - 影响评估: -->

### DEV-1
- 原 DU 建议: requirement-design §1/§8 与 story-design 假设 ES 版本「8.17.x，Boot BOM 已含 8.17.x 管理」；全量遍历类需求若按 `_id` 排序实现深分页（ES 传统写法）。
- 实际实现: 服务端形态（compose 镜像、Testcontainers）固定 8.17.4；客户端依赖不写版本，Spring Boot 3.5.15 BOM 实际解析 elasticsearch-java **8.18.8**（本地 .m2 解析实证）。8.18 Client / ES 8 服务端禁止对 `_id` 做 fielddata 排序，同一合并提交中的索引适配器 `infrastructure/elasticsearch/EsSearchIndexAdapter.java`（第 243–294 行 `allIds`，CHG-0021 全量 dump 路径）改用 **PIT（openPointInTime，keepAlive 1m）+ `_shard_doc` asc + search_after 游标**，finally closePointInTime 兜底。
- 原因: 版本严格继承 BOM（task-design 硬性要求不写死版本），Boot 3.5.15 BOM 已将 ES client 升到 8.18.x；`_id` fielddata 默认禁用是 ES 8 的确定性行为，PIT + _shard_doc 是官方推荐的深分页/全量 scroll 替代。
- 影响评估: 本 DU 的查询链路（DU-BE-502/511）走 from/size（size≤100、浅分页），不受影响；8.18 客户端对 8.17.4 服务端兼容，冒烟与查询 IT 均连 8.17.4 容器执行；后续 CHG-0021 全量重建直接复用该深分页模式。

### DEV-2
- 原 DU 建议: story-design §1 命名 `infrastructure.health.ElasticsearchHealthIndicator`，仅述「自定义 HealthIndicator 调 ping」。
- 实际实现: 类名为 `infrastructure.health.SearchHealthIndicator`（Bean 名 searchHealthIndicator → components.search）；application.yml 显式 `management.health.elasticsearch.enabled: false` 关闭 Spring Boot 默认 ES 健康指示器。
- 原因: 默认 ElasticsearchReactiveHealthIndicator 面向 Spring Data ES 自动配置、且会另挂 components.elasticsearch 项，与本服务「自定义 ping 单一健康项」口径重复；类名按域语义命名为 Search。
- 影响评估: 健康契约仍为 story-design 冻结的 `components.search=UP/DOWN`；默认项关闭后无重复健康组件，无功能影响。

## 自检

对照 task-spec.md Verification：

- ✅ AC-002：依赖为官方 `co.elastic.clients:elasticsearch-java`（pom 第 79–97 行无版本号），全工程无 RestHighLevelClient 引用（grep 0 命中）；版本解析自 Boot BOM（3.5.15 → 8.18.8）。
- ✅ AC-003：SearchHealthIndicator ping 成功 UP、异常吞为 DOWN 不外抛（`SearchHealthIndicator.java` 第 29–40 行）；Client Bean 构造不连接，ES 停止进程存活。
- ✅ AC-005：Testcontainers 冒烟 1 例（ElasticsearchSmokeTest.indexTwoDocumentsAndSearchHits：临时索引写 2 文档检索命中并清理）+ 上下文 1 例（MallSearchApplicationSmokeTest.contextLoads 离线可过）。
- ✅ Unit：ContextLoadsTest 以 MallSearchApplicationSmokeTest.contextLoads 落地，test profile H2 + Nacos 关闭，无 ES 也能加载。
- ✅ Integration：AbstractElasticsearchTest static 单例 ES 8.17.4（withSecurityDisabled），经 mall.elasticsearch.uris 动态注入——同时实证 MALL_ES_URIS/配置覆盖（AC-004）。
- ✅ API：本 DU 无业务端点；health 经 actuator `/actuator/health` components.search 暴露（show-components/show-details=always）。
- ✅ Migration：N/A（mall_search 数据源/Flyway 保留但 enabled 默认 false，本 DU 不建表）。
- ✅ Error Case：ES 不可达时 ping 异常 → DOWN 仅 warn，不向调用方抛出、不退出进程。
- 说明：`mall-services/mall-search/target/surefire-reports` 当前不存在（未保留测试报告），以上用例数按测试源码 @Test 方法枚举（CHG-0020 本 DU 相关 2 类 2 例），未杜撰执行数字；联调运行证据归 Integration Gate。
