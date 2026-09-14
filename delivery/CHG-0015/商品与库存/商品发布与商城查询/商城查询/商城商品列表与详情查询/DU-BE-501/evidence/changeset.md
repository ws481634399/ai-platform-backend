# Changeset — DU-BE-501

> 仓库：repo-1（ai-platform-backend），分支 M3-dev。共 5 个任务提交，文件清单如下。

## 任务1 mall-common-web：@StringId

| 文件 | 变更类型 |
|---|---|
| `mall-common/mall-common-web/src/main/java/com/ai/mall/common/web/annotation/StringId.java` | 新增 |
| `mall-common/mall-common-web/src/test/java/com/ai/mall/common/web/annotation/StringIdJacksonTest.java` | 新增 |

## 任务2 mall-common-security：内部共享凭证

| 文件 | 变更类型 |
|---|---|
| `mall-common/mall-common-security/pom.xml` | 修改（补 mall-common-core、jakarta.servlet-api） |
| `mall-common/mall-common-security/src/main/java/com/ai/mall/common/security/InternalIdentityFilter.java` | 新增 |
| `mall-common/mall-common-security/src/main/java/com/ai/mall/common/security/InternalSecurityProperties.java` | 新增 |
| `mall-common/mall-common-security/src/main/java/com/ai/mall/common/security/config/InternalSecurityAutoConfiguration.java` | 新增 |
| `mall-common/mall-common-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 新增 |
| `mall-common/mall-common-security/src/test/java/com/ai/mall/common/security/InternalIdentityFilterTest.java` | 新增 |

## 任务3 mall-gateway：白名单显式化 + internal 404 外拒

| 文件 | 变更类型 |
|---|---|
| `mall-gateway/src/main/java/com/ai/mall/gateway/security/GatewaySecurityConfiguration.java` | 修改 |
| `mall-gateway/src/test/java/com/ai/mall/gateway/security/CHG0015GatewaySecurityChainTest.java` | 新增 |

## 任务4/5 mall-product：字符串 ID + 价区 + 有效 SKU 可见性 + 内部凭证

| 文件 | 变更类型 |
|---|---|
| `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/mall/dto/MallProductDtos.java` | 修改 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/mall/MallProductController.java` | 修改 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/admin/dto/ProductDtos.java` | 修改 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/admin/dto/CategoryDtos.java` | 修改 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/admin/dto/BrandDtos.java` | 修改 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/admin/ProductAdminController.java` | 修改 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/admin/CategoryAdminController.java` | 修改 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/admin/BrandAdminController.java` | 修改 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/internal/dto/ProductSnapshotView.java` | 修改 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/application/product/ProductApplicationService.java` | 修改 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/domain/product/ProductRepository.java` | 修改 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/infrastructure/persistence/product/ProductRepositoryImpl.java` | 修改 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/infrastructure/persistence/product/SkuMapper.java` | 修改 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/infrastructure/persistence/product/PriceRangePo.java` | 新增 |
| `mall-services/mall-product/src/main/java/com/ai/mall/product/infrastructure/config/ProductSecurityConfiguration.java` | 修改 |
| `mall-services/mall-product/src/main/resources/application.yml` | 修改 |
| `mall-services/mall-product/src/test/java/com/ai/mall/product/interfaces/rest/mall/MallProductApiTest.java` | 修改 |
| `mall-services/mall-product/src/test/java/com/ai/mall/product/interfaces/rest/admin/CategoryAdminApiTest.java` | 修改 |
| `mall-services/mall-product/src/test/java/com/ai/mall/product/interfaces/rest/internal/InternalProductApiTest.java` | 修改 |
| `mall-services/mall-product/src/test/java/com/ai/mall/product/support/ApiTestSecurityConfig.java` | 修改 |

## mall-inventory：分页修复 + 字符串 ID + 内部凭证 + 403 映射

| 文件 | 变更类型 |
|---|---|
| `mall-services/mall-inventory/pom.xml` | 修改（补 mybatis-plus-jsqlparser） |
| `mall-services/mall-inventory/src/main/java/com/ai/mall/inventory/infrastructure/config/MybatisPlusConfig.java` | 新增 |
| `mall-services/mall-inventory/src/main/java/com/ai/mall/inventory/infrastructure/client/SkuClient.java` | 修改 |
| `mall-services/mall-inventory/src/main/java/com/ai/mall/inventory/infrastructure/config/InventorySecurityConfiguration.java` | 修改 |
| `mall-services/mall-inventory/src/main/java/com/ai/mall/inventory/interfaces/rest/admin/InventoryAdminController.java` | 修改 |
| `mall-services/mall-inventory/src/main/java/com/ai/mall/inventory/interfaces/rest/admin/dto/InventoryDtos.java` | 修改 |
| `mall-services/mall-inventory/src/main/java/com/ai/mall/inventory/interfaces/rest/internal/dto/InternalInventoryDtos.java` | 修改 |
| `mall-services/mall-inventory/src/main/java/com/ai/mall/inventory/interfaces/rest/admin/InventorySecurityExceptionAdvice.java` | 新增 |
| `mall-services/mall-inventory/src/main/resources/application.yml` | 修改 |
| `mall-services/mall-inventory/src/test/java/com/ai/mall/inventory/interfaces/rest/admin/InventoryAdminApiTest.java` | 新增 |
| `mall-services/mall-inventory/src/test/java/com/ai/mall/inventory/interfaces/rest/internal/InternalInventorySecurityTest.java` | 修改 |
| `mall-services/mall-inventory/src/test/java/com/ai/mall/inventory/support/ApiTestSecurityConfig.java` | 修改 |
