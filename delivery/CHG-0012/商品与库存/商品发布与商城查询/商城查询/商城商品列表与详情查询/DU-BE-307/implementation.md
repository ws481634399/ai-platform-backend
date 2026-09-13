# DU Implementation — DU-BE-307

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

商城端商品公开查询能力，仅返回 ON_SALE 商品，不含库存字段。

### 接口层

- `interfaces/rest/mall/MallProductController.java`（新增）：`GET /api/mall/products`（列表，支持 categoryId 筛选、page/size 分页，按创建时间倒序）、`GET /api/mall/products/{id}`（详情，非 ON_SALE 返回 404）。
- `interfaces/rest/mall/dto/MallProductDtos.java`（新增）：MallProductListItemView（id/name/code/mainImageUrl/minPriceInCents/categoryId/brandId/status）、MallProductDetailView（含 skus 列表，SkuView 不含 stock）。

### 应用层

- `application/product/ProductApplicationService.java`：新增 `mallPage(query)`、`getMallById(id)`，内部委托仓储并硬过滤 ON_SALE。

### 领域层

- `domain/product/ProductRepository.java`：新增 `mallPage(MallProductQuery query)` 端口方法。

### 基础设施

- `infrastructure/persistence/product/ProductRepositoryImpl.java`：实现 mallPage，`.eq(ProductPo::getStatus, ProductStatus.ON_SALE.name())` 硬过滤，`.orderByDesc(ProductPo::getCreatedAt)`。
- `infrastructure/config/ProductSecurityConfiguration.java`：`/api/mall/**` permitAll（商城公开访问）。

### 测试

- `interfaces/rest/mall/MallProductApiTest.java`（新增）：6 个用例覆盖列表仅 ON_SALE、分类筛选、分页、倒序、详情成功、非 ON_SALE 404、不含库存字段。

## Commits

| Commit | DU | 消息 | 文件数 |
| --- | --- | --- | --- |
| f0a26b3 | DU-BE-307 | feat(CHG-0012): 商品上下架与发布、商城查询、内部契约快照 | 32 |

## Deviations

无。

## 自检

- [x] mallPage 硬过滤 status=ON_SALE
- [x] 列表按创建时间倒序
- [x] 非 ON_SALE 详情返回 404
- [x] 详情响应不含 stock 字段
- [x] /api/mall/** permitAll 无需认证
- [x] MallProductApiTest 6 用例全绿
