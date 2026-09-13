# DU Implementation — DU-BE-404

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。

## 变更内容

库存确认扣减，订单支付成功后调用，将已锁定库存转为实际扣减。

### 领域层

- `domain/inventory/Inventory.java`：`confirmDeduction(quantity)` 校验 locked >= quantity，同时减少 totalQuantity 和 lockedQuantity。
- `domain/inventory/InventoryReservation.java`：`confirmDeduction()` 状态 LOCKED → DEDUCTED，幂等。

### 应用层

- `application/inventory/InventoryApplicationService.java`：`confirmDeduction(reservationId)` → 幂等查重 → confirmDeduction → update + saveReservation(DEDUCTED) + insertLog(DEDUCT)。

### 接口层

- `interfaces/rest/internal/InternalInventoryController.java`：`POST /api/internal/inventory/confirm`。

## Commits

| Commit | DU | 消息 |
| --- | --- | --- |
| 08d619f | DU-BE-401/402/403/404 | feat(inventory): 库存核心领域模型与持久化层 |
| e2bff4b | DU-BE-401/402/403/404 | feat(inventory): 库存应用服务与管理端/内部接口 |

## Deviations

无。

## 自检

- [x] 确认扣减后 total 和 locked 同时减少
- [x] 确认扣减幂等：已扣减再扣减不变
- [x] 扣减数量超过锁定数量时拒绝（B2203）
- [x] 扣减操作写入流水（operationType=DEDUCT）
