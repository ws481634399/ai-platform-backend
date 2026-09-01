# DU Task — DU-BE-002

> Repository Delivery 的 DU 级任务细化（Expected Implementation）。
> 权威来源：workspace-source.tasks 中本 DU 小节；本文件是 repo 侧可执行副本，
> Agent 依据权威来源填写，允许按仓内实际情况微调并保持一致。
>
> Implementation Guidance 提示：Implementation Sketch 必填；
> Pseudocode 条件必填（本 DU 未声明触发器，未命中时写 N/A + 理由）；Verification 必填。
> 本文件固定为 Expected Implementation（Plan / Sketch / Pseudocode / Verification），
> 与 implementation.md（Actual Implementation）分立，不得合并。

## 1. Goal

8 个业务服务 + gateway 完成依赖接入与配置统一——MyBatis-Plus/Flyway/MySQL 驱动服务直连、common-web/log/test 引用、Nacos discovery 预留（默认关闭）、application.yml 统一结构（数据源/Flyway/MyBatis-Plus/logging 环境变量化）、各服务 db/migration 目录规范、9 个 @SpringBootTest 上下文冒烟测试，并完成全量构建与测试验证留证。

## 2. Repository

repo-1（implementation/ai-platform-backend）

## 3. Scope

- mall-services 8 模块（identity/member/product/cart/order/inventory/search/system）POM 增量：`mybatis-plus-spring-boot3-starter`（mybatis-plus-bom 托管）+ `flyway-core`/`flyway-mysql`（Boot BOM 托管）+ `mysql-connector-j`（runtime，Boot BOM 托管）+ `spring-cloud-starter-alibaba-nacos-discovery`（SCA BOM 托管）+ mall-common-web/mall-common-log 依赖 + mall-common-test（test scope）
- 8 服务 application.yml 统一结构：application name / server.port（沿用 8101~8108 现有分配）/ datasource（`${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DB_<svc>}` + `${MYSQL_USER:root}`/`${MYSQL_PASSWORD:}`）/ `spring.flyway.enabled: ${FLYWAY_ENABLED:false}` + `locations: classpath:db/migration` / `mybatis-plus.configuration.map-underscore-to-camel-case: true` / `spring.cloud.nacos.server-addr: ${NACOS_ADDR:}` + `discovery.enabled: ${NACOS_ENABLED:false}` / logging（pattern 含 %X{traceId}）
- 各服务 `src/main/resources/db/migration/` 目录规范（含 .gitkeep，本阶段不提交业务表 DDL）
- mall-gateway：POM 增 nacos discovery 预留（不依赖 common-web，WebFlux 栈隔离）+ application.yml 增 Nacos 占位
- 测试资产：8 业务服务 + gateway 各 1 个 @SpringBootTest 上下文冒烟（Nacos 默认关闭；业务服务以 H2 test profile 提供 DataSource——MODE=MySQL，Flyway 在 H2 上执行验证配置工程正确性；gateway 无数据源直接冒烟）
- 全量验证：`mvn clean package -DskipTests`（AC-03）与 `mvn test`（AC-11）BUILD SUCCESS 留证
- 禁止事项：服务间实现依赖（AC-12）；BOM 版本覆盖声明（AC-05）；真实密码入库；启动断言 Nacos/MySQL 在场

## 4. Design References

- design.md §2.1 方案概要 / §2.5 模块依赖方向（目标态）/ §2.6 服务 Skeleton 补全 / §2.7 Persistence Foundation / §2.8 Test Foundation（冒烟测试部分）/ §3.1 repo-1 修改概要 / §4 集成边界（环境变量注入点）

## 5. Dependencies

DU-BE-001（common-web/log/test 能力就绪并已 `mvn install`）

## 6. Acceptance Criteria

- AC-01 环境核验留证（Java=21 且 Maven>=3.9）
- AC-02 Reactor 清单复验（24 项目在列，无目录/module 异常）
- AC-03 全量构建 BUILD SUCCESS 留证
- AC-04 单模块 `mvn clean package -pl mall-services/mall-order -am -DskipTests` BUILD SUCCESS 留证
- AC-05 抽样服务 POM 无版本覆盖声明
- AC-07 contracts 复验无污染（本 DU 未触碰）
- AC-08 gateway+8 服务独立主类/独立打包逐一核验
- AC-09 抽验服务 MyBatis-Plus/Flyway 配置正确且无跨服务库访问
- AC-11 `mvn test` 全绿且含最小测试集
- AC-12 全仓复验（无循环依赖、服务间无实现依赖、core 零反向依赖）

## 7. Implementation Sketch

```text
目标依赖方向（design §2.5）:
mall-services/* ──→ mall-common-web ──→ mall-common-core（单向）
      │    └────────→ mall-common-log（logback/mall-logback-base.xml 随 jar 提供）
      └─(test scope)→ mall-common-test（starter-test + h2 聚合）
mall-services/* ──直连──→ mybatis-plus-spring-boot3-starter / flyway-core+flyway-mysql / mysql-connector-j(runtime)
mall-gateway ──→ nacos-discovery（预留）──× mall-common-web（WebFlux 栈隔离，禁止依赖）

配置注入链（环境变量 → application.yml 占位，为 ENG-M0-004 留接入点）:
MYSQL_HOST/PORT/DB_<svc>/USER/PASSWORD → spring.datasource.*
FLYWAY_ENABLED(false) + locations → spring.flyway.*
NACOS_ADDR("") + NACOS_ENABLED(false) → spring.cloud.nacos.*

冒烟测试形态: @SpringBootTest + test profile（H2 MODE=MySQL 数据源 + Flyway 执行）→ context loads
```

## 8. Pseudocode

N/A（依赖接入 + 配置统一 + 标准 @SpringBootTest 冒烟，未命中 business-flow/algorithm/state-transition/orchestration 任一触发器；组件关系与环境变量注入链已在 Implementation Sketch 给出）

## 9. Verification

- Integration（构建）: `mvn clean package -DskipTests` Reactor 24 项目全 BUILD SUCCESS（AC-02/03）；`mvn clean package -pl mall-services/mall-order -am -DskipTests`（AC-04）
- Integration（测试）: `mvn test` 全绿，新增测试数 ≥ 11（core/web 单测与切片 + 8 服务冒烟 + gateway 冒烟 + common-test 占位）（AC-11）
- Static（边界复验）: 抽样服务 POM 无 `<version>` 覆盖声明（AC-05）；`mvn dependency:tree` 复验服务间无实现依赖、无循环（AC-12）；contracts 扫描无污染（AC-07）
- Error Case: 无 MySQL/无 Nacos 环境下 `mvn test` 仍通过（默认关闭生效，禁启动断言）；Flyway 在 H2 test profile 执行成功验证迁移目录工程正确性；真实 MySQL/Nacos 集成项记录 Pending M0 Integration Verification（AC-09 集成部分）
