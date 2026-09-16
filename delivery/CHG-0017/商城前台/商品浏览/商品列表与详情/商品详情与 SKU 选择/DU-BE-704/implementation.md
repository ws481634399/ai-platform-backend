# DU Implementation — DU-BE-704

> DU 级实施记录（Actual Implementation）。

## 变更内容

### 商品详情增强（GET /api/mall/products/{id}）
- `MallProductDetailView` 新增：`brandName`、`categoryPath`、`dimensionsOrder`、`specDimensions`、`skuIndex`
- `toDetailView()` 装配：
  - **brandName**：调 `BrandApplicationService.getById`，缺失/异常给 null（不 500）
  - **categoryPath**：`buildCategoryPath` 沿 `parent_id` 上溯 ≤3 层，根在前，断链给已上溯段
  - **specDimensions**：遍历启用 SKU specs，`LinkedHashMap<String, LinkedHashSet<String>>` 归并，维度名首次出现序，值去重保序
  - **skuIndex**：组合键 = 按维度顺序 `value1|value2` → `{skuId, priceFen, imageUrl, status}`
- 矩阵规则：仅启用 SKU 参与；缺规格值跳过矩阵但保留 skus 列表；组合键冲突取 skuId 较小一条并 warn
- 404 口径沿用 `getMallById`：不存在 / 非 ON_SALE / 无启用 SKU → 404

### DTO 新增
- `CategoryPathView(id, name)`、`SpecDimensionView(name, values)`、`SkuIndexEntryView(skuId, priceFen, imageUrl, status)`

## Commits

| Hash | 类型 | 说明 |
|------|------|------|
| 8496b53 | feat | 商品详情增强：brandName/categoryPath/specDimensions/skuIndex |

## Deviations

无。

## 自检

- brandName + categoryPath + specDimensions + skuIndex —— MallProductDetailEnhancedTest::detailWithMatrix
- 404 不存在 —— detailNotFound
- 404 无启用 SKU —— detailNoEnabledSku
- 组合键冲突防御（取 skuId 较小）—— 代码内 warn 日志
- mall-product 全量 93 passed
