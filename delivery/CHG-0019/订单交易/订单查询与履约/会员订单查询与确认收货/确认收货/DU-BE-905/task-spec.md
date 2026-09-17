# DU Task Spec — DU-BE-905

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本 DU 跨 STORY-004-03-01-02（确认收货）与 STORY-004-03-02-01（后台查询发货）两个 Story，一次实现。

## 0. 元信息

- DU id: DU-BE-905
- Change ID: CHG-0019
- Feature Path: 订单交易/订单查询与履约/会员订单查询与确认收货/确认收货
- 权威来源: STORY-004-03-01-02 story-design.md §5、STORY-004-03-02-01 story-design.md §5 / DU-BE-905

## 任务清单

- [ ] 任务 1 — Order.confirmReceipt() 与 ReceiptService：SHIPPED→COMPLETED CAS + completedAt + history；首次/幂等/非法三态（verifies: TC-001, TC-002, TC-003, TC-004, TC-006）
- [ ] 任务 2 — POST /api/mall/orders/{orderNo}/confirm-receipt（verifies: TC-001, TC-002, TC-003）
- [ ] 任务 3 — admin 检索：pageForAdmin（orderNo 精确/memberId/status/时间）与 getDetailByOrderNo（无归属）（verifies: TC-001, TC-002）
- [ ] 任务 4 — ShipmentService.ship：PAID→SHIPPED CAS（物流两列+shippedAt+SHIP history,operator=username）；重复幂等；非法 B0407（verifies: TC-003, TC-004）
- [ ] 任务 5 — AdminOrderController 三端点 + @PreAuthorize（order:list/order:view/order:ship）+ ShipRequest 校验（verifies: TC-005, TC-006）
- [ ] 任务 6 — 权限矩阵测试（无权限码 ADMIN 403、MEMBER 403、401）（verifies: TC-006）

> 任务 1/2 的 TC 编号对应 STORY-004-03-01-02 test-design.md；任务 3–6 对应 STORY-004-03-02-01 test-design.md（两 Story 各自从 TC-001 起编号）。

## Acceptance Criteria

> 本 DU 承接两个 Story 的 AC（各自 Story 内编号），按 Story 分组列出。

STORY-004-03-01-02（确认收货）：

- [ ] AC-001 — 本人 SHIPPED 确认 → COMPLETED/completedAt/CONFIRM_RECEIPT history，无库存调用。
- [ ] AC-002 — PENDING/PAID/CANCELLED → B0407；他人/不存在 404。
- [ ] AC-003 — COMPLETED 重复确认幂等 200，无新 history。

STORY-004-03-02-01（后台查询与发货）：

- [ ] AC-001 — admin 多条件组合检索与分页排序正确，可跨会员。
- [ ] AC-002 — admin 详情含 memberId 与完整轨迹。
- [ ] AC-003 — PAID ship → SHIPPED/物流列/shippedAt/SHIP history(operator)。
- [ ] AC-004 — 非 PAID ship → B0407；重复 ship 幂等且一条 SHIP。
- [ ] AC-005 — 物流字段缺失/超长 → 400；订单不存在 404。
- [ ] AC-006 — 无权限码/MEMBER → 403；无 token 401。

## 执行顺序（Execution Order）

1. 任务 1/2（收货，小）→ 3 → 4 → 5 → 6。

## 并行度（Parallelization）

任务 1/2 与任务 3 可并行。

## Verification

- Unit: Order.confirmReceipt/ship 状态解释。
- Integration: H2 CAS；latch 双确认；多条件分页造单；多权限 JWT 403 矩阵。
- API: 会员 confirm-receipt 200/404/409；admin 200/400/403/404/409。
- Migration: N/A（V1 列已含 completed_at/delivery_*/shipped_at）。
- Error Case: 重复/非法/越权三类全部覆盖。
