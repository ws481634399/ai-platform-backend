# Changeset — DU-BE-302

| 仓库 | 文件 | 变更类型 |
|---|---|---|
| repo-1 | `pom.xml` | 修改（compiler 开启 -parameters） |
| repo-1 | `mall-gateway/src/main/resources/application.yml` | 修改（商品服务管理端路由） |
| repo-1 | `mall-services/mall-identity/src/main/resources/db/migration/V3__add_product_category_brand_permissions.sql` | 新增（8 权限码 + 3 菜单 + 超管授权） |
| repo-1 | `mall-services/mall-product/pom.xml` | 修改（security/resource-server 依赖） |
| repo-1 | `mall-services/mall-product/src/main/resources/application.yml` | 修改（mall.security.jwt.*） |
| repo-1 | `mall-services/mall-product/src/main/resources/db/migration/V1__create_product_category_and_brand.sql` | 新增（分类/品牌两表） |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/domain/shared/MasterDataStatus.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/domain/shared/ProductErrorCode.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/domain/category/CategoryLevel.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/domain/category/Category.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/domain/category/CategoryException.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/domain/category/CategoryRules.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/domain/category/CategoryRepository.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/application/category/CategoryCommands.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/application/category/CategoryApplicationService.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/infrastructure/persistence/category/CategoryPo.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/infrastructure/persistence/category/CategoryMapper.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/infrastructure/persistence/category/CategoryRepositoryImpl.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/infrastructure/config/ProductSecurityConfiguration.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/admin/CategoryAdminController.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/admin/ProductSecurityExceptionAdvice.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/admin/dto/CategoryDtos.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/admin/dto/CategoryTreeAssembler.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/test/java/com/ai/mall/product/domain/category/CategoryRulesTest.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/test/java/com/ai/mall/product/domain/category/CategoryTest.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/test/java/com/ai/mall/product/interfaces/rest/admin/CategoryAdminApiTest.java` | 新增 |
