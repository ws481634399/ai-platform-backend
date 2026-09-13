# DU Task Spec — DU-BE-305

> 权威 DU 划分：story-design.md §5 / DU-BE-305
> verifies 绑定 TC-NNN

## 0. 元信息

- DU id: DU-BE-305
- Change ID: CHG-0011
- Feature Path: 商品与库存/商品与 SKU 管理/SKU 与规格/SKU 与规格管理
- 权威来源: story-design.md §5 / DU-BE-305

## 任务清单

- [ ] 任务 1 — Flyway V3 建 product_sku 表（verifies: TC-004）
- [ ] 任务 2 — Money 值对象与 Sku 实体、SpecificationHash 计算（verifies: TC-003, TC-004）
- [ ] 任务 3 — Product 聚合 addSku/updateSku/changeSkuPrice/enableSku/disableSku 行为与事件（verifies: TC-007）
- [ ] 任务 4 — SkuMapper 与 ProductRepository SKU 持久化（verifies: TC-001）
- [ ] 任务 5 — ProductAdminAppService SKU 方法（verifies: TC-001, TC-002, TC-005, TC-008）
- [ ] 任务 6 — Controller /skus 子资源与权限注解（verifies: TC-006）

## Acceptance Criteria

- [ ] AC-003 — 一 Product 可建多 SKU
- [ ] AC-004 — SKU Code 全局唯一
- [ ] AC-005 — 价格分(long) >= 0，无浮点
- [ ] AC-006 — 同商品规格组合唯一
- [ ] AC-008 — 修改后查询一致
- [ ] AC-013 — 无权限 403
- [ ] AC-014 — SKU 领域事件注册
- [ ] AC-015 — SKU 独立启停

## 执行顺序

1. 任务 1 → 2 → 3 → 4 → 5 → 6

## 并行度

无

## Verification

- Unit: Sku/Money/SpecificationHash 单测 + Product.addSku 行为
- Integration: ProductAdminAppServiceTest（H2）
- API: ProductAdminControllerTest（MockMvc，/skus 子资源）
- Migration: V3 执行，uk 断言
- Error Case: 编码重复、组合重复、负价格、并发同编码（DB uk 兜底）
