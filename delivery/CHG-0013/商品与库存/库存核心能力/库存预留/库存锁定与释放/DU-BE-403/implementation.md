# DU Implementation — DU-BE-403

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。

## 变更内容

库存锁定与释放，供订单侧调用。使用 SQL 条件更新保证并发安全，reservationId 作为幂等键。

### 领域层

- `domain/inventory/Inventory.java`：`lock(quantity)` 校验 available >= quantity，增加 lockedQuantity；`release(quantity)` 校验 locked >= quantity，减少 lockedQuantity。
- `domain/inventory/InventoryReservation.java`（新增）：预留实体。状态 LOCKED → RELEASED/DEDUCTED。`release()` 幂等。
- `domain/inventory/ReservationStatus.java`（新增）：枚举 LOCKED/RELEASED/DEDUCTED。

### 应用层

- `application/inventory/InventoryApplicationService.java`：`lock(reservationId, skuId, quantity)` → 幂等查重 → `lockStock` SQL 条件更新（rows=0 抛 STOCK_INSUFFICIENT）→ saveReservation(LOCKED) + insertLog(LOCK)。`release(reservationId)` → 幂等 → release → saveReservation(RELEASED) + insertLog(RELEASE)。

### 接口层

- `interfaces/rest/internal/InternalInventoryController.java`（新增）：`POST /api/internal/inventory/lock`、`POST /release`，供 mall-order 调用。

### 基础设施

- `infrastructure/persistence/inventory/InventoryMapper.java`：`@Update` lockStock 条件更新 SQL：`UPDATE inventory_stock SET locked_quantity = locked_quantity + ? WHERE sku_id = ? AND (total_quantity - locked_quantity) >= ?`。
- `db/migration/V2__create_inventory_reservation.sql`（新增）：inventory_reservation 表（reservation_id 唯一）。

## Commits

| Commit | DU | 消息 |
| --- | --- | --- |
| 08d619f | DU-BE-401/402/403/404 | feat(inventory): 库存核心领域模型与持久化层 |
| e2bff4b | DU-BE-401/402/403/404 | feat(inventory): 库存应用服务与管理端/内部接口 |

## Deviations

无。

## 自检

- [x] 锁定使用 SQL 条件更新，并发安全
- [x] 可用库存不足时锁定返回 409（B2204）
- [x] reservationId 幂等：重复锁定返回已有预留
- [x] 释放幂等：已释放再释放不变
- [x] 锁定/释放均写入流水
