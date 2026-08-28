# ai-platform-backend

AI 智能电商微服务平台 · 后端 Monorepo（M0 工程骨架）。

## 技术基线

| 项 | 版本 |
| --- | --- |
| Java | 21（enforcer 强制，`[21,22)`） |
| Maven | 3.9+（不提交 Wrapper） |
| Spring Boot | 3.5.15 |
| Spring Cloud | 2025.0.3 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| 编码 | UTF-8（源码与资源） |

## 模块结构（24 个 Maven 项目）

```text
backend（仓库根 = Maven 根，聚合 + enforcer 守门）
├── mall-bom              # 唯一版本权威（packaging=pom，无 parent，仅被 import）
├── mall-common           # 公共技术层（聚合）
│   ├── mall-common-core / web / security / redis / mq / openfeign / log / test
├── mall-contracts        # 跨服务契约层（聚合）
│   ├── mall-api-contracts / mall-event-contracts
├── mall-gateway          # 网关应用（Spring Cloud Gateway，端口 8080）
└── mall-services         # 业务服务层（聚合）
    ├── mall-identity(8101) / mall-member(8102) / mall-product(8103) / mall-cart(8104)
    ├── mall-order(8105) / mall-inventory(8106) / mall-search(8107) / mall-system(8108)
```

## 环境要求

- JDK 21（非 21 环境构建会由 enforcer 显式失败）
- Maven 3.9+
- 本仓库不包含 Maven Wrapper，请使用本机安装的 Maven

## 常用命令

```bash
# 全量构建（根聚合入口）
mvn clean package -DskipTests

# 构建单个应用（示例：订单服务）
mvn clean package -DskipTests -pl mall-services/mall-order -am

# 本地启动单个应用（Fat Jar）
java -jar mall-services/mall-order/target/mall-order-1.0.0-SNAPSHOT.jar

# 查看某模块依赖树（核对版本来源）
mvn dependency:tree -pl mall-services/mall-order
```

## 版本治理约定

1. 所有第三方版本**只允许**在 `mall-bom/pom.xml` 声明；业务/公共模块 POM 一律不写 `<version>`（parent 除外）；
2. `mall-bom` 通过 `import` 统一引入 Spring Boot / Spring Cloud / Spring Cloud Alibaba / MyBatis-Plus BOM，并直接管理 MapStruct、Springdoc；
3. Lombok 版本由 `spring-boot-dependencies` 托管，不重复声明；
4. 新增依赖时先在 `mall-bom` 补版本（或确认已被导入 BOM 管理），再在模块 POM 声明坐标。

## 依赖边界（M0）

- `mall-services/*` → `mall-common/*`（M0 仅骨架依赖声明）
- `mall-contracts` 零第三方依赖，纯 DTO/事件契约定位
- 服务之间**禁止** Maven 依赖（跨服务协作走契约 + 运行期调用）
- `mall-common` / `mall-contracts` 不出现任何业务领域代码

> M0 仅工程骨架：各应用可独立编译、打包、启动，不含业务功能、Nacos/DB/RocketMQ 实际接入。
