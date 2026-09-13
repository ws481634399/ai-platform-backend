# DU Task Spec — DU-BE-403

## 0. 元信息

- DU id: DU-BE-403
- Change ID: CHG-0013
- Feature Path: 商品与库存/库存核心能力/库存预留/库存锁定与释放
- 权威来源: story-design.md §5 / DU-BE-403

## 任务清单

- [ ] 任务 1 — Flyway V2 建 inventory_reservation 表（verifies: TC-001）
- [ ] 任务 2 — InventoryReservation 实体 + ReservationStatus 枚举 + Repository 方法（verifies: TC-001, TC-003）
- [ ] 任务 3 — lockStock SQL 条件更新实现（verifies: TC-001, TC-002, TC-007）
- [ ] 任务 4 — InventoryApplicationService.lock/release + 幂等 + 流水（verifies: TC-001~TC-006）
- [ ] 任务 5 — InternalInventoryController lock/release 接口（verifies: TC-001, TC-004）

## Acceptance Criteria

- [ ] AC-010 — 锁定 available>=quantity → 成功，locked+=quantity，reservation LOCKED
- [ ] AC-011 — 锁定 available<quantity → STOCK_INSUFFICIENT
- [ ] AC-012 — 同一 reservationId 重复锁定 → 返回原结果
- [ ] AC-013 — 释放基于 LOCKED reservation → locked-=quantity，状态 RELEASED
- [ ] AC-014 — 同一 reservationId 重复释放 → 不重复减少 locked
- [ ] AC-018 — 锁定/释放记录 LOCK/RELEASE 流水
- [ ] AC-019 — 并发锁定最后 1 件 → 仅一个成功

## 执行顺序（Execution Order）

1. 任务 1（迁移）
2. 任务 2（实体+Repository）
3. 任务 3（SQL 条件更新）
4. 任务 4（应用服务+幂等+流水）
5. 任务 5（控制器）

## 并行度（Parallelization）

无

## Verification

- Unit: Inventory.lock/release 领域行为
- Integration: @SpringBootTest + MockMvc + H2
- API: /api/internal/inventory/lock、/release 契约测试
- Migration: Flyway V2 执行验证
- Error Case: 库存不足/reservation 不存在/重复操作
- 并发: 多线程锁定测试
