# DU Implementation — DU-BE-306

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

Product 聚合根新增上下架发布能力，基于 CHG-0011 已建立的 Product/Sku 聚合扩展状态流转行为。

### 领域层

- `domain/product/Product.java`：新增 `domainEvents` 列表（`new ArrayList<>()`）、`publish()`（校验主图存在、至少一 ENABLED SKU、ENABLED SKU 价格非负、非 DISABLED 状态，成功后 status=ON_SALE 并注册 ProductPublishedDomainEvent）、`unpublish()`（校验 ON_SALE，成功后 status=OFF_SALE 并注册 ProductUnpublishedDomainEvent）、`getDomainEvents()`/`clearDomainEvents()`。
- `domain/product/event/ProductDomainEvent.java`（新增）：领域事件标记接口。
- `domain/product/event/ProductPublishedDomainEvent.java`（新增）：record(productId)。
- `domain/product/event/ProductUnpublishedDomainEvent.java`（新增）：record(productId)。
- `domain/product/ProductException.java`：新增 `publishValidationFailed(msg)`、`alreadyOnSale()`、`notOnSale()` 工厂方法。
- `domain/shared/ProductErrorCode.java`：新增 `B2150 PUBLISH_VALIDATION_FAILED`、`B2151 PRODUCT_ALREADY_ON_SALE`、`B2152 PRODUCT_NOT_ON_SALE`。

### 应用层

- `application/product/ProductApplicationService.java`：新增 `publish(id)`、`unpublish(id)`，从仓储加载 Product 后调用聚合行为并保存。

### 接口层

- `interfaces/rest/admin/ProductAdminController.java`：新增 `POST /{id}/publish`、`POST /{id}/unpublish`，权限码 `product:product:publish`。

### 基础设施

- `infrastructure/config/ProductSecurityConfiguration.java`：`/api/mall/**` permitAll（商城端公开访问）。
- `mall-identity/src/main/resources/db/migration/V5__add_product_publish_permission.sql`（新增）：向 sys_permission 插入 `product:product:publish` 权限编码。

### 测试

- `interfaces/rest/admin/ProductAdminApiTest.java`：新增 8 个用例覆盖上架成功/无主图拒绝/无 ENABLED SKU 拒绝/DISABLED 拒绝/下架成功/重新上架/权限 403。

## Commits

| Commit | DU | 消息 | 文件数 |
| --- | --- | --- | --- |
| f0a26b3 | DU-BE-306/307/308 | feat(CHG-0012): 商品上下架与发布、商城查询、内部契约快照 | 32 |

## Deviations

无。

## 自检

- [x] publish() 校验主图、ENABLED SKU、价格合法、非 DISABLED
- [x] 状态流转 DRAFT/OFF_SALE → ON_SALE → OFF_SALE
- [x] 领域事件注册（ProductPublishedDomainEvent / ProductUnpublishedDomainEvent）
- [x] 权限码 product:product:publish 通过 V5 迁移入库
- [x] ApiTest 覆盖成功/失败/权限场景
