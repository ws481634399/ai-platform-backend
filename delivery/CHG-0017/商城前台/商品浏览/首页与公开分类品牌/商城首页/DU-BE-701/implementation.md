# DU Implementation — DU-BE-701

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

### 商城首页聚合（GET /api/mall/home）
- `MallHomeController`：`GET /api/mall/home`（匿名，网关白名单已在 DU-BE-702 加好），返回 `UnifyResult<HomeView>`
- `HomeApplicationService.home()`：三路只读聚合
  - `categoryEntries`：`categoryService.mallTree()` 过滤 `parentId==ROOT_PARENT_ID && isEnabled()`，按 `sort` 升序再 `id` 升序，`limit(8)`，字段 `id/name/iconImageUrl(null)`
  - `newArrivals`：复用 `productService.mallPage(page=1,size=10)`（已含 ON_SALE + EXISTS 启用 SKU + 价区填充），取 `records` + `priceRanges` 装配卡片
  - `recommends`：复用 `newArrivals` 列表，标 `source=FALLBACK_NEWEST`
  - `banners`：空数组 `List.of()`
- `MallHomeDtos`：`HomeView(categoryEntries, newArrivals, recommends, banners)`、`CategoryEntryView`、`ProductCardView(id,name,mainImageUrl,minPrice,maxPrice)`、`RecommendView(...+source)`、`BannerView`，所有 long id 加 `@StringId`

## Commits

| Hash | 类型 | 说明 |
|------|------|------|
| 868ee02 | feat | 商城首页聚合接口（分类入口+新品+推荐fallback+banners空） |

## Deviations

无。

## 自检

- 分类入口仅启用根分类，≤8，sort 序 —— MallHomeApiTest.homeAggregation / categoryEntriesLimit8 验证
- 新品仅 ON_SALE + 至少一个启用 SKU，价区填充 —— homeAggregation（商品三无启用 SKU 不出现在列表）
- 推荐 source=FALLBACK_NEWEST，与新品同列表 —— homeAggregation 验证
- banners 返回空数组 `[]` —— homeAggregation / homeEmpty 验证
- ID 输出为字符串 —— `@StringId` 注解（categoryEntries[0].id value("1")）
- mall-product 全量测试 84 passed
