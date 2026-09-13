# DU Task Spec — DU-BE-404

## 0. 元信息

- DU id: DU-BE-404
- Change ID: CHG-0013
- Feature Path: 商品与库存/库存核心能力/库存确认/库存确认扣减
- 权威来源: story-design.md §5 / DU-BE-404

## 任务清单

- [ ] 任务 1 — Inventory.confirmDeduction 领域方法（verifies: TC-001, TC-003）
- [ ] 任务 2 — InventoryApplicationService.confirmDeduction + 状态机 + 幂等 + DEDUCT 流水（verifies: TC-001, TC-002, TC-003, TC-004）
- [ ] 任务 3 — InternalInventoryController confirm 接口（verifies: TC-001）

## Acceptance Criteria

- [ ] AC-015 — 确认扣减基于 LOCKED reservation → total-=quantity、locked-=quantity，状态 DEDUCTED
- [ ] AC-016 — 同一 reservationId 重复确认 → 不重复扣减
- [ ] AC-017 — 基于非 LOCKED reservation 确认 → INVALID_STATE
- [ ] AC-018 — 确认扣减记录 DEDUCT 流水

## 执行顺序（Execution Order）

1. 任务 1（领域方法）
2. 任务 2（应用服务+状态机+流水）
3. 任务 3（控制器）

## 并行度（Parallelization）

无

## Verification

- Unit: Inventory.confirmDeduction 状态机测试
- Integration: @SpringBootTest + MockMvc + H2（先锁定再确认）
- API: /api/internal/inventory/confirm 契约测试
- Migration: N/A（复用 V2）
- Error Case: reservation 不存在/非 LOCKED 状态/重复确认
