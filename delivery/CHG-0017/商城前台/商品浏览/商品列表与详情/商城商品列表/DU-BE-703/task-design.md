# DU Task Design — DU-BE-703

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

商城商品列表：分页（size≤50）、子孙分类展开（深度≤3）、brandIds 多选交集、四种排序（价区排序走派生表 SQL，禁止内存排序）、价区填充与可售过滤。

## 2. Repository

repo-1（mall-product 8103）

## 3. Scope

- interfaces `GET /api/mall/products`（公开）：参数 page/size(默认/最大 50)、categoryId、brandIds（逗号）、sort=default|newest|price_asc|price_desc。
- repository 自定义查询（Mapper XML/注解）：FROM product p LEFT JOIN (SELECT product_id, MIN(sale_price) lo, MAX(sale_price) hi FROM product_sku WHERE status='ENABLED' GROUP BY product_id) pr ON pr.product_id=p.id；WHERE p.status='ON_SALE' AND pr.product_id IS NOT NULL（EXISTS 等价）+ category_id IN (子孙 id 集) + brand_id IN (...)；ORDER BY：default/newest = p.listed_at DESC；price_asc=pr.lo ASC, p.listed_at DESC（同分稳定 tie-breaker）；price_desc=pr.hi DESC, p.listed_at DESC。
- 子孙分类：categoryId 命中后查其子树 id 集（递归 CTE 或两层内存展开，深度 ≤3，缓存可后置不做）。
- 参数收敛：size 1..50（51 → 400）；非法 sort 静默回落 default；未知参数忽略；空 brandIds 不拼条件。
- DTO：records[].id 字符串、minPrice/maxPrice number 整数分非 null。

## 4. Design References

- CHG-0017 requirement-design.md §2.3（列表查询与排序派生表）、§4（/products 契约）；STORY-003-02-02-01 story-design.md §1/§2/§4；价区 SSOT 在 CHG-0015。

## 5. Dependencies

权威表：无。实际依赖 DU-BE-501（价区分组 SQL/EXISTS、@StringId、白名单）。

## 6. Implementation Sketch

- Query 对象组装；count 与分页由 MyBatis-Plus PaginationInnerInterceptor（product 侧已具备）生成，自定义 SQL 需保证 count 查询可生成（JOIN 派生表在 MySQL 下 count 包装可行；验证 total）。
- brandIds 解析：去空、去重、上限（如 ≤50 个）防 IN 过长；category 子树为空（叶子不存在）直接返空页而不是报错。
- 排序白名单 enum 映射，列名禁止字符串拼接进 SQL（防注入）：sort → 固定 ORDER BY 片段。
- 翻页正确性：全量翻页断言下架/无启用 SKU 商品不出现。

## 7. Pseudocode

N/A（metadata `pseudocode: false`）。SQL 组装为参数化直线逻辑；无算法。
