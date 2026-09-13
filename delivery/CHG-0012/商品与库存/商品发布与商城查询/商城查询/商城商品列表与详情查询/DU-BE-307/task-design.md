# DU Task Design — DU-BE-307

## 1. Goal

MallProductController 商城列表（ON_SALE 过滤）与详情 API。

## 2. Repository

repo-1（ai-platform-backend）

## 3. Scope

- mall-product: interfaces.rest.mall.MallProductController、ProductRepository.mallPage

## 4. Design References

- story-design.md §1 模块改动、§2 接口契约

## 5. Dependencies

DU-BE-306（ON_SALE 商品数据）

## 6. Implementation Sketch

- MallProductController：GET /api/mall/products 列表，硬过滤 status=ON_SALE，支持 keyword/categoryId/brandId/page/size，orderBy createdAt desc。GET /{id} 详情，非 ON_SALE 返回 404。
- ProductRepository：增加 mallPage(query) 或在 page 中固定 status=ON_SALE。

## 7. Pseudocode

N/A（标准查询过滤，无复杂逻辑）
