# DU Task Spec — DU-BE-903

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-903 引用该表，不新造 DU。

## 0. 元信息

- DU id: DU-BE-903
- Change ID: CHG-0019
- Feature Path: 订单交易/支付与取消/模拟支付与订单取消/模拟支付与订单取消
- 权威来源: story-design.md §5 / DU-BE-903

## 任务清单

- [ ] 任务 1 — Order 聚合 pay()/cancel() 行为（首次迁移/重复幂等/非法拒绝，history 仅真实迁移）（verifies: TC-011）
- [ ] 任务 2 — OrderMapper/Repository transitionStatus：CAS SQL（status+version 条件、时间列、history 同事务）（verifies: TC-001, TC-006）
- [ ] 任务 3 — PaymentService：加载归属→状态解释→CAS→事务外逐行 confirm；失败保持 PAID + ERROR 日志（verifies: TC-001, TC-002, TC-003, TC-010）
- [ ] 任务 4 — OrderCancelService：同构 cancel（reason 校验/长度）→ release（verifies: TC-004, TC-005）
- [ ] 任务 5 — pay/cancel REST 端点与 CancelRequest 校验（verifies: TC-003, TC-005）
- [ ] 任务 6 — mall-inventory Mapper 加固：releaseStock/deductStock 条件 SQL、reservation casStatus（verifies: TC-007, TC-012）
- [ ] 任务 7 — InventoryApplicationService release/confirm 重写：reservation CAS 仲裁 + 目标态幂等 + 冲突拒绝 + log（verifies: TC-007, TC-008）
- [ ] 任务 8 — pay‖cancel 并发竞争测试与 inventory 并发重复测试（50 轮）（verifies: TC-006, TC-007）
- [ ] 任务 9 — inventory 既有测试全量回归（verifies: TC-009）

## Acceptance Criteria

- [ ] AC-001 — PENDING pay → PAID/paidAt/PAY history；reservation 全 DEDUCTED、stock total/locked 同减。
- [ ] AC-002 — 重复 pay 幂等 200；confirm 仅一次；无新 history。
- [ ] AC-003 — CANCELLED pay → B0407；他人/不存在 404。
- [ ] AC-004 — PENDING cancel → CANCELLED/cancelledAt/reason/CANCEL history；reservation RELEASED、可用量归还。
- [ ] AC-005 — 重复 cancel 幂等且 release 一次；PAID/SHIPPED/COMPLETED cancel → B0407；他人 404。
- [ ] AC-006 — pay‖cancel 并发终态唯一且库存终态一致，无撕裂。
- [ ] AC-007 — inventory 同 reservationId 重复 release/confirm 库存只变一次；非法态冲突报错。
- [ ] AC-008 — inventory 既有测试全绿；新增 CAS 测试通过。
- [ ] AC-009 — 库存副作用异常订单不回滚，ERROR 日志含 orderNo/traceId。

## 执行顺序（Execution Order）

1. 任务 1/2 → 6/7（inventory 可先行）→ 3/4 → 5 → 8/9。

## 并行度（Parallelization）

任务 6/7（mall-inventory 模块内）与任务 1/2（mall-order 域/持久层）并行。

## Verification

- Unit: 聚合状态解释；inventory service CAS 分支（目标态/冲突/赢家）。
- Integration: H2 真实 CAS；mock inventoryPort 断言调用次数；CountDownLatch 50 轮 pay‖cancel。
- API: 200/400/404/409；幂等重放响应。
- Migration: N/A。
- Error Case: confirm/release 5xx 不回滚订单；日志断言。
