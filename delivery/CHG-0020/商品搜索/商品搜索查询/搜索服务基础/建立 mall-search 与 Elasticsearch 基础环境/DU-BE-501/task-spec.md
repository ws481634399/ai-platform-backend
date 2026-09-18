# DU Task Spec — DU-BE-501

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本 DU 对应 STORY-005-01-01-01。

## 0. 元信息

- DU id: DU-BE-501
- Change ID: CHG-0020
- Feature Path: 商品搜索/商品搜索查询/搜索服务基础/建立 mall-search 与 Elasticsearch 基础环境
- 权威来源: STORY-005-01-01-01 story-design.md §5 / DU-BE-501

## 任务清单

- [ ] 任务 1 — pom 引入官方 ES 依赖（版本不写死）并 dependency:tree 验证（verifies: TC-002@01-01-01）
- [ ] 任务 2 — ElasticsearchConfiguration（URI/超时/MALL_ES_URIS 覆盖）与配置项（verifies: TC-004@01-01-01）
- [ ] 任务 3 — ElasticsearchHealthIndicator（UP/DOWN，异常不抛出）（verifies: TC-003@01-01-01, TC-006@01-01-01）
- [ ] 任务 4 — AbstractElasticsearchIT + ElasticsearchSmokeIT（临时索引写读 2 文档）（verifies: TC-005@01-01-01）

## Acceptance Criteria

STORY-005-01-01-01（ES 基础环境）：

- [ ] AC-002 — 依赖为官方 elasticsearch-java，无 RestHighLevelClient，版本由 BOM 管理。
- [ ] AC-003 — 连 ES 成功；ES 停止 health DOWN 且进程不退出。
- [ ] AC-005 - mvn -pl mall-services/mall-search -am test 全绿（冒烟+上下文加载）。

## 执行顺序（Execution Order）

1. 任务 1/2 → 3/4。

## 并行度（Parallelization）

任务 3 与 4 可并行。

## Verification

- Unit: ContextLoadsTest 离线通过（Client Bean 懒连接）。
- Integration: Testcontainers ES 8.17.4 冒烟（临时索引写读）；ES 停止 health DOWN 进程存活。
- API: N/A（本 DU 不暴露业务端点；health 经 actuator 验证 UP/DOWN）。
- Migration: N/A。
- Error Case: ES 不可达时 ping 异常被吞为 DOWN，不向调用方抛出。
