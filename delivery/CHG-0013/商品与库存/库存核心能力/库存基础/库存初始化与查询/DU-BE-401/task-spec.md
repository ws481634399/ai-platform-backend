# DU Task Spec — DU-BE-401

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。

## 0. 元信息

- DU id: DU-BE-401
- Change ID: CHG-0013
- Feature Path: 商品与库存/库存核心能力/库存基础/库存初始化与查询
- 权威来源: story-design.md §5 / DU-BE-401

## 任务清单

- [ ] 任务 1 — Flyway V1 建 inventory_stock/inventory_log 表 + identity V4 权限 + 网关路由（verifies: TC-010）
- [ ] 任务 2 — Inventory 聚合根与 InventoryLog 实体 + Repository 端口与实现（verifies: TC-001, TC-005）
- [ ] 任务 3 — InventoryApplicationService init/get/batchGet/page + SkuClient SKU 校验（verifies: TC-001, TC-002, TC-003, TC-004, TC-005, TC-006, TC-007）
- [ ] 任务 4 — InventoryAdminController + DTO + 安全配置（verifies: TC-001, TC-009）
- [ ] 任务 5 — INIT 流水记录（verifies: TC-008）

## Acceptance Criteria

- [ ] AC-001 — 合法 skuId+totalQuantity>=0 初始化 → 200，total=初始值、locked=0、available=total
- [ ] AC-002 — skuId 不存在 → INVALID_ARGUMENT
- [ ] AC-003 — 重复初始化 → CONFLICT
- [ ] AC-004 — totalQuantity<0 → INVALID_ARGUMENT
- [ ] AC-005 — 单查返回 total/locked/available，available=total-locked
- [ ] AC-006 — 批查返回各 SKU 库存
- [ ] AC-007 — 后台分页查询库存列表
- [ ] AC-018 — 初始化记录 INIT 流水
- [ ] AC-020 — 无 inventory:stock:* 权限 → 403
- [ ] AC-021 — inventory_stock 表无 product 主数据字段

## 执行顺序（Execution Order）

1. 任务 1（迁移+权限+路由）
2. 任务 2（领域模型+Repository）
3. 任务 3（应用服务）
4. 任务 4（控制器+安全）
5. 任务 5（流水）

## 并行度（Parallelization）

无

## Verification

- Unit: Inventory 聚合不变量测试
- Integration: @SpringBootTest + MockMvc + H2，mock SkuClient
- API: /api/admin/inventory/stocks 契约测试
- Migration: Flyway V1 + identity V4 执行验证
- Error Case: SKU 不存在/重复初始化/负库存/无权限
