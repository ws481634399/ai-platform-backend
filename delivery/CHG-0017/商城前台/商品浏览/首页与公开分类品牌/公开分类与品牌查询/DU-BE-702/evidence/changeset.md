# Changeset — DU-BE-702

> 仓库：repo-1（ai-platform-backend），分支 M3-dev。提交 b4b5a1f。

## 新增文件

| 文件 | 说明 |
|------|------|
| mall-product/interfaces/rest/mall/MallCategoryController.java | GET /api/mall/categories/tree |
| mall-product/interfaces/rest/mall/MallBrandController.java | GET /api/mall/brands |
| mall-product/interfaces/rest/mall/dto/MallCatalogDtos.java | CategoryNode / BrandView / BrandPageView（@StringId） |
| mall-product/interfaces/rest/mall/dto/MallCategoryTreeAssembler.java | 启用分类树组装（禁用整枝剪除） |
| mall-product/test/.../mall/MallCatalogApiTest.java | 5 例集成测试 |

## 修改文件

| 文件 | 说明 |
|------|------|
| mall-product/application/category/CategoryApplicationService.java | 新增 mallTree() |
| mall-product/application/brand/BrandApplicationService.java | 新增 mallPage(keyword,page,size)，MALL_MAX_PAGE_SIZE=200 |
| mall-product/infrastructure/persistence/brand/BrandRepositoryImpl.java | page() keyword 改 LIKE ESCAPE 转义；拆分 if 块避免 ECJ 重载推断 |
| mall-gateway/resources/application.yml | mall-product-mall 路由 predicate 扩展 categories/brands/home/skus |
| mall-gateway/security/GatewaySecurityConfiguration.java | permitAll 追加 /api/mall/categories/**,/api/mall/brands/**,/api/mall/home,/api/mall/skus/** |
