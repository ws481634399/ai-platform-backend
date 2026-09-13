# DU Implementation — DU-BE-308

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

内部服务间商品 SKU 快照查询契约，供订单/购物车等下游服务在下单时获取商品当前价格与状态。

### 接口层

- `interfaces/rest/internal/InternalProductController.java`（新增）：`GET /api/internal/products/{productId}/skus/{skuId}`，返回 ProductSnapshotView。商品或 SKU 不存在、或 SKU 不属于该商品时返回 404。
- `interfaces/rest/internal/dto/ProductSnapshotView.java`（新增）：record(productId, skuId, productName, skuName, skuAttributes, price, image, currentStatus)。price 单位为分（long），skuAttributes 为 Map<String,String>。

### 应用层

- `application/product/ProductApplicationService.java`：新增 `getSkuSnapshot(productId, skuId)`，加载 Product 聚合后定位 SKU，组装快照视图。skuName 由规格拼接，image 取 SKU 主图或商品主图。

### 测试

- `interfaces/rest/internal/InternalProductApiTest.java`（新增）：3 个用例覆盖完整快照字段、商品不存在 404、SKU 不属于商品 404。

## Commits

| Commit | DU | 消息 | 文件数 |
| --- | --- | --- | --- |
| f0a26b3 | DU-BE-308 | feat(CHG-0012): 商品上下架与发布、商城查询、内部契约快照 | 32 |

## Deviations

无。

## 自检

- [x] ProductSnapshotView 包含 productId/skuId/productName/skuName/skuAttributes/price/image/currentStatus
- [x] price 单位为分（long）
- [x] 商品不存在返回 404
- [x] SKU 不属于商品返回 404
- [x] InternalProductApiTest 3 用例全绿
