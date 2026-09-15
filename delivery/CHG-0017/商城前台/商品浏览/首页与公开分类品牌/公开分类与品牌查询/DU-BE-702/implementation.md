# DU Implementation — DU-BE-702

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

### 公开分类树（GET /api/mall/categories/tree）
- `MallCategoryController`：`/api/mall/categories/tree`，调用 `CategoryApplicationService.mallTree()`
- `CategoryApplicationService.mallTree()`：复用 `findAll()` 全量加载，剪枝在 assembler 层完成
- `MallCategoryTreeAssembler.toTree(List<Category>)`：
  - 建 childrenByParent map，从 `ROOT_PARENT_ID=0` 出发 DFS
  - **禁用节点不入树且不递归其子**（整枝剪除：禁用父即使有启用子也一并剪除）
  - 同层按 sort 升序、再 id 升序
- `MallCatalogDtos.CategoryNode(@StringId long id, String name, int sort, List<CategoryNode> children)`

### 公开品牌分页（GET /api/mall/brands?keyword=&page=&size=）
- `MallBrandController`：`/api/mall/brands`，调用 `BrandApplicationService.mallPage(keyword, page, size)`
- `BrandApplicationService.mallPage()`：强制 `status=ENABLED`；`MALL_MAX_PAGE_SIZE=200`（区别于管理端 `MAX_PAGE_SIZE=100`）；size null→默认20、page null→1、<1→1
- `BrandRepositoryImpl.page()`：keyword 模糊查询改为 `LOWER(name) LIKE CONCAT('%', LOWER({0}), '%') ESCAPE '!'`，`escapeLike()` 转义 `%`/`_`/`!`（兼容 MySQL 与 H2 MODE=MySQL）
- `MallCatalogDtos.BrandView(@StringId long id, String name, String logoUrl, int sort)`、`BrandPageView(items, total, page, size)`（`brand.logo` → `logoUrl`）

### 网关
- `application.yml`：`mall-product-mall` 路由 predicate 追加 `/api/mall/categories/**,/api/mall/brands/**,/api/mall/home,/api/mall/skus/**`（本 Story 先写全后续路径）
- `GatewaySecurityConfiguration.java`：permitAll 白名单追加 `/api/mall/categories/**, /api/mall/brands/**, /api/mall/home, /api/mall/skus/**`

## Commits

| Hash | 类型 | 说明 |
|------|------|------|
| b4b5a1f | feat | 公开分类树与品牌查询接口 + 网关白名单 |

## Deviations

无。

## 自检

- 分类树仅返回启用节点，禁用父整枝剪除（含启用子）—— MallCatalogApiTest.categoryTreeEnabledOnlyAndPrune 验证
- 品牌仅 ENABLED，keyword 模糊、size≤200、id 字符串 —— MallCatalogApiTest.brandsEnabledKeywordAndSizeCap 验证
- 空态返回 [] 结构 —— 空分类树/空品牌测试验证
- 通配符 % 被转义不当作 LIKE 通配 —— brandKeywordWildcardEscaped 验证
- 网关白名单：匿名 categories/brands 可访问（GatewaySecurityConfiguration permitAll）
- mall-product 全量测试 75 passed；mall-gateway 19 passed
