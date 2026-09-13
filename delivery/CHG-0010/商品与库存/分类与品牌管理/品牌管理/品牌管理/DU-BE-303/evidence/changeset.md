# Changeset — DU-BE-303

| 仓库 | 文件 | 变更类型 |
|---|---|---|
| repo-1 | `mall-services/mall-product/pom.xml` | 修改（新增 mybatis-plus-jsqlparser） |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/domain/brand/Brand.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/domain/brand/BrandRepository.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/domain/brand/BrandException.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/application/brand/BrandCommands.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/application/brand/BrandApplicationService.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/infrastructure/persistence/brand/BrandPo.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/infrastructure/persistence/brand/BrandMapper.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/infrastructure/persistence/brand/BrandRepositoryImpl.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/infrastructure/config/MybatisPlusConfig.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/admin/BrandAdminController.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/main/java/com/ai/mall/product/interfaces/rest/admin/dto/BrandDtos.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/test/java/com/ai/mall/product/domain/brand/BrandTest.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/test/java/com/ai/mall/product/interfaces/rest/admin/BrandAdminApiTest.java` | 新增 |
| repo-1 | `mall-services/mall-product/src/test/java/com/ai/mall/product/support/ApiTestSecurityConfig.java` | 新增（测试共享安全切片，分类测试同步改用） |
| repo-1 | `mall-services/mall-product/src/test/java/com/ai/mall/product/interfaces/rest/admin/CategoryAdminApiTest.java` | 修改（改用共享 ApiTestSecurityConfig） |
