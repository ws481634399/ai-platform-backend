# DU Implementation — DU-BE-002

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库，Workspace 仅保留引用。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task.md（Expected Implementation）分立，不得合并。

## 变更内容

### 8 业务服务依赖接入（POM 增量，模板一致）
identity/member/product/cart/order/inventory/search/system 每模块新增：
- `mall-common-web`（${project.version}，WebMVC + 统一响应/异常/TraceId/OpenAPI 传递）
- `mall-common-log`（logback 基础资产随 jar）
- `mybatis-plus-spring-boot3-starter` / `flyway-core` / `flyway-mysql`（Boot BOM、mybatis-plus-bom 托管，无版本声明）
- `mysql-connector-j`（runtime）
- `spring-cloud-starter-alibaba-nacos-discovery`（SCA BOM 托管，默认关闭）
- `mall-common-test`（test scope）
- 替换原直接依赖的 `spring-boot-starter-web`（经 mall-common-web 传递）

### 8 业务服务 application.yml 统一结构
- application name / server.port（8101~8108 沿用原分配）
- datasource：`jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DB_<SVC>:mall_<svc>}?...` + `${MYSQL_USER:root}` / `${MYSQL_PASSWORD:}`
- `spring.flyway.enabled: ${FLYWAY_ENABLED:false}` + `locations: classpath:db/migration`
- `mybatis-plus.configuration.map-underscore-to-camel-case: true`
- `spring.cloud.nacos.server-addr: ${NACOS_ADDR:}` + `discovery.enabled: ${NACOS_ENABLED:false}`
- logging.pattern.console 含 `%X{traceId:-}`

### 测试资产
- 8 服务各 1 份 `src/test/resources/application-test.yml`：H2 内存库（MODE=MySQL + DATABASE_TO_LOWER + CASE_INSENSITIVE_IDENTIFIERS）+ Flyway enabled + Nacos disabled
- 8 服务各 1 个 `@SpringBootTest + @ActiveProfiles("test")` 上下文冒烟测试

### db/migration 目录规范
- 8 服务各建 `src/main/resources/db/migration/`（含 .gitkeep，本阶段不提交业务表 DDL）

### mall-gateway（WebFlux 栈隔离）
- POM：增 nacos discovery 预留 + mall-common-test（test scope）；未依赖 mall-common-web
- application.yml：增 Nacos 占位（NACOS_ADDR/NACOS_ENABLED，默认关闭）
- 1 个 @SpringBootTest 上下文冒烟测试（无数据源）

## Commits

无（本地实施完成，尚未提交；提交待用户确认后执行）

## Deviations

无

## 自检

- AC-01 环境核验：enforcer Rule 0（Maven>=3.9）/ Rule 1（Java 21）passed；运行日志确认 Java 21.0.12
- AC-02 Reactor 清单：`mvn clean package -DskipTests` Reactor 24/24 项目 SUCCESS（2026-09-01 22:10，01:53 min）
- AC-03 全量构建：同上 BUILD SUCCESS
- AC-04 单模块构建：`mvn clean package -pl mall-services/mall-order -am -DskipTests` BUILD SUCCESS（8/8，10.9s）
- AC-05 抽样 POM：8 服务 + gateway 新增依赖均无版本声明（由 BOM 托管）
- AC-08 独立打包：9 个主类（Mall<svc>Application）逐一在列；repackage 日志确认 9 模块 Fat Jar 独立打包
- AC-09 数据访问配置：8 服务冒烟日志确认 H2 连接建立、Flyway 创建 schema history（空迁移集）、MyBatis-Plus 就绪、Nacos 未启用
- AC-11 全量测试：`mvn test` Reactor 24/24 SUCCESS（57.8s）——core 17 + web 11 + 9 上下文冒烟 + test 聚合器占位，全绿
- AC-12 依赖方向：mall-services/* → mall-common-web → mall-common-core 单向；gateway 不依赖 mall-common-web（WebFlux 隔离）；服务间零实现依赖
- 沙箱环境构建统一追加 `-Dmaven.repo.local=d:\Desktop\ai-platform\.m2-sandbox`，属环境适配，非实现偏离
