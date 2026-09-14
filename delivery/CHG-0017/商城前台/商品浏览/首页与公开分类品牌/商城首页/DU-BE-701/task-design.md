# DU Task Design — DU-BE-701

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

商城首页聚合接口 GET /api/mall/home：分类入口、新品、推荐（占位 fallback 新品）、banners（空占位），匿名公开。

## 2. Repository

repo-1（mall-product 8103）

## 3. Scope

- interfaces `GET /api/mall/home`（公开，网关 permitAll）；HomeController + HomeAppService。
- HomeView 装配器：categoryEntries（启用根分类，sort 序，取 ≤8，字段 id/name/iconImageUrl）、newArrivals（ON_SALE + EXISTS 启用 SKU，listed_at DESC LIMIT 10，卡片字段同列表 item）、recommends（M3 占位：source=FALLBACK_NEWEST，取新品同结果；运营位实体预留不建表）、banners（空数组占位 []）。
- 复用 DU-BE-501 价区填充器（同条分组 SQL MIN/MAX）填充卡片价区。
- DTO @StringId；空数据各数组返回 [] 非 null。

## 4. Design References

- CHG-0017 requirement-design.md §2.1（首页聚合）、§4（/home 契约）；STORY-003-02-01-01 story-design.md §1/§2/§4；价区 SSOT 见 CHG-0015 requirement-design §5.1。

## 5. Dependencies

权威表：无。实际依赖 DU-BE-501（价区分组 SQL/EXISTS 谓词、@StringId、网关白名单机制）。

## 6. Implementation Sketch

- 一次请求内三路只读查询并行（无强一致要求，顺序执行也可，M3 数据量小）：根分类（status=ENABLED, parent_id IS NULL ORDER BY sort）→ 商品页查（ON_SALE + EXISTS enabled sku + ORDER BY listed_at DESC LIMIT 10）→ 价区批量填充 → newArrivals；recommends 复用同一列表并标 source。
- 商品卡字段：id(字符串)/name/mainImageUrl/minPrice/maxPrice。
- 空态：无商品 → []；HTTP 始终 200（除非依赖故障：单路失败按部分降级，分类失败仍返回商品，反之亦然；整体数据访问异常 503）。

## 7. Pseudocode

N/A（metadata `pseudocode: false`）。纯只读聚合装配，无算法。
