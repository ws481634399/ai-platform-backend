# DU Task Design — DU-BE-308

## 1. Goal

InternalProductController 内部 SKU 查询 + ProductSnapshot DTO。

## 2. Repository

repo-1（ai-platform-backend）

## 3. Scope

- mall-product: interfaces.rest.internal.InternalProductController、ProductSnapshotView DTO、ProductApplicationService.getSkuSnapshot

## 4. Design References

- story-design.md §1 模块改动、§2 接口契约

## 5. Dependencies

DU-BE-306（Product/SKU 数据）

## 6. Implementation Sketch

- ProductSnapshotView：record 含 productId/skuId/productName/skuName/skuAttributes(Map)/price(long)/image/currentStatus。
- ProductApplicationService.getSkuSnapshot(productId, skuId)：加载 Product，查找 SKU，组装 DTO。
- InternalProductController：GET /api/internal/products/{id}/skus/{skuId}，返回 ProductSnapshotView。

## 7. Pseudocode

N/A（DTO 组装，无复杂逻辑）
