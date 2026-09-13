# DU Task Spec — DU-BE-402

## 0. 元信息

- DU id: DU-BE-402
- Change ID: CHG-0013
- Feature Path: 商品与库存/库存核心能力/库存调整/库存调整与流水
- 权威来源: story-design.md §5 / DU-BE-402

## 任务清单

- [ ] 任务 1 — Inventory.adjust(delta, reason) 领域方法，校验 total+delta>=0（verifies: TC-001, TC-002）
- [ ] 任务 2 — InventoryApplicationService.adjust + ADJUST 流水（verifies: TC-001, TC-002, TC-003）
- [ ] 任务 3 — InventoryAdminController 调整接口 + 流水查询接口（verifies: TC-001）

## Acceptance Criteria

- [ ] AC-008 — 调整库存（正/负 delta）→ total 更新正确，流水记录 before/after/delta/reason/operator
- [ ] AC-009 — 调整导致 total<0 → 拒绝 INVALID_ARGUMENT
- [ ] AC-018 — 调整记录 ADJUST 流水

## 执行顺序（Execution Order）

1. 任务 1（领域方法）
2. 任务 2（应用服务+流水）
3. 任务 3（控制器）

## 并行度（Parallelization）

无

## Verification

- Unit: Inventory.adjust 正负 delta 与防负测试
- Integration: @SpringBootTest + MockMvc + H2
- API: /api/admin/inventory/stocks/{skuId}/adjust 契约测试
- Migration: N/A（复用 V1）
- Error Case: 库存不存在/负 delta 导致负库存/delta=0
