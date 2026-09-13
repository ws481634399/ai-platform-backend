# DU Task Design — DU-BE-403

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。

## 1. Goal

inventory_reservation 表、锁定/释放 API、SQL 条件更新、幂等、LOCK/RELEASE 流水。

## 2. Repository

repo-1（ai-platform-backend）

## 3. Scope

- mall-inventory: domain.inventory.InventoryReservation 实体 + ReservationStatus 枚举
- mall-inventory: domain.inventory.InventoryRepository 补充 lockStock（SQL 条件更新）、findReservationByReservationId、saveReservation
- mall-inventory: application.inventory.InventoryApplicationService 补充 lock/release
- mall-inventory: interfaces.rest.internal.InternalInventoryController（lock/release）
- mall-inventory: Flyway V2（inventory_reservation）

## 4. Design References

- requirement-design.md §2 提议方案（SQL 条件更新防超卖）
- story-design.md §1 模块改动、§2 接口契约、§3 数据变更

## 5. Dependencies

DU-BE-401

## 6. Implementation Sketch

```
InternalInventoryController.lock
  └── InventoryApplicationService.lock
        ├── InventoryRepository.findReservationByReservationId  // 幂等：已存在返回
        ├── InventoryRepository.lockStock(skuId, quantity)      // SQL 条件更新
        │     UPDATE inventory_stock SET locked=locked+? WHERE sku_id=? AND (total-locked)>=?
        ├── InventoryRepository.saveReservation(LOCKED)
        └── InventoryRepository.insertLog(LOCK)

InternalInventoryController.release
  └── InventoryApplicationService.release
        ├── InventoryRepository.findReservationByReservationId
        ├── 若状态已 RELEASED → 幂等返回成功
        ├── 若状态 LOCKED → locked-=quantity, save, 状态 RELEASED
        └── InventoryRepository.insertLog(RELEASE)
```

## 7. Pseudocode

命中 complexity-trigger: state-transition（reservation 状态机）。

```
lock(reservationId, skuId, quantity):
  reservation = findReservationByReservationId(reservationId)
  if reservation != null:
      return reservation  // 幂等
  stock = findBySkuId(skuId)
  if stock == null: throw NOT_FOUND
  rows = lockStock(skuId, quantity)  // UPDATE ... WHERE (total-locked)>=quantity
  if rows == 0: throw STOCK_INSUFFICIENT
  saveReservation(new Reservation(reservationId, skuId, quantity, LOCKED))
  insertLog(LOCK, skuId, quantity, before=locked, after=locked+quantity)

release(reservationId):
  reservation = findReservationByReservationId(reservationId)
  if reservation == null: throw NOT_FOUND
  if reservation.status == RELEASED: return  // 幂等
  if reservation.status == DEDUCTED: throw INVALID_STATE
  // status == LOCKED
  locked -= quantity
  reservation.status = RELEASED
  save(reservation)
  insertLog(RELEASE, ...)
```
