# DU Implementation — DU-BE-703

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。

## 变更内容

### 商城商品列表增强（GET /api/mall/products）
- `MallProductController.page()` 新增 `brandIds`（List<Long>）、`sort` 参数；`brandId` 单值兼容合并进 brandIds
- `ProductApplicationService.mallPage()`：
  - size 上限 50（`MALL_MAX_PAGE_SIZE`，区别于管理端 100）
  - `collectDescendantCategoryIds(rootId)`：子孙分类展开（深度 ≤3，内存两轮展开），分类不存在返空页
  - `normalizeBrandIds`：去空、去重、上限 50
  - `MallProductSort.from()` 非法值回落 DEFAULT
- `ProductMapper.selectMallPage()` 自定义 SQL：
  - `LEFT JOIN (SELECT product_id, MIN(sale_price) lo, MAX(sale_price) hi FROM product_sku WHERE status='ENABLED' GROUP BY product_id) pr`
  - `WHERE p.status='ON_SALE' AND pr.product_id IS NOT NULL`（EXISTS 等价）
  - 可选 `category_id IN (...)` / `brand_id IN (...)` / `product_name LIKE`
  - ORDER BY 白名单：default/newest = `p.created_at DESC`；price_asc = `pr.lo ASC, p.created_at DESC`；price_desc = `pr.hi DESC, p.created_at DESC`
- `MallProductSort` 枚举（DEFAULT/NEWEST/PRICE_ASC/PRICE_DESC）
- `MallProductListItemView.minPrice/maxPrice` 改 `long` 非空（派生表保证存在）
- `ProductPageQuery`（application + domain）扩展 `brandIds`/`categoryIds`/`sort`

## Commits

| Hash | 类型 | 说明 |
|------|------|------|
| 751a792 | feat | 商品列表增强：brandIds 多选、子孙分类、价区派生表排序、size≤50 |

## Deviations

无。

## 自检

- brandIds 多选交集 —— MallProductListEnhancedTest::brandIdsMultiSelect
- 子孙分类展开 —— descendantCategory
- price_asc/price_desc 走派生表排序 —— sortByPrice
- 非法 sort 回落 default —— invalidSortFallback
- size>50 收敛 50 —— sizeCap50
- 价区非空 long —— priceRangeNonNull
- mall-product 全量 90 passed
