# DU Task Spec — DU-BE-701

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-701
- Change ID: CHG-0017
- Feature Path: 商城前台/商品浏览/首页与公开分类品牌/商城首页
- 权威来源: story-design.md §5 / DU-BE-701

## 任务清单

- [ ] 任务 1 — /home Controller+AppService 与响应骨架（categoryEntries/newArrivals/recommends/banners）（verifies: TC-001）
- [ ] 任务 2 — ON_SALE+EXISTS 启用 SKU + listed_at DESC LIMIT 10 查询，价区填充，recommends.source=FALLBACK_NEWEST（verifies: TC-002）
- [ ] 任务 3 — 空数据 [] 空态与部分降级（verifies: TC-003）
- [ ] 任务 4 — 网关匿名放行（白名单配置）（verifies: TC-001）

## Acceptance Criteria

- [ ] AC-001 — 匿名 GET /home 200，四段结构齐全且数据真实。
- [ ] AC-002 — 商品位仅 ON_SALE 且有启用 SKU；上架时间倒序 LIMIT 10；recommends.source=FALLBACK_NEWEST；无商品时 []，接口仍 200。

## 执行顺序（Execution Order）

1. 任务 1 → 2 → 3；任务 4 与开发并行（依赖 DU-BE-501 白名单模式）。

## 并行度（Parallelization）

任务 4（网关配置）与 1-3 并行。

## Verification

- Unit: 装配器字段/source 断言、空态 []。
- Integration: @SpringBootTest 造 ON_SALE/下架/无 SKU 三类商品断言过滤与排序。
- API: 经 8080 匿名 200。
- Migration: N/A。
- Error Case: 单路数据故障部分降级不 500；整体故障 503。
