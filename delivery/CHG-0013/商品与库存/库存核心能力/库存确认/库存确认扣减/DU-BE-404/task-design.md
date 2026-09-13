# DU Task Design — DU-BE-404

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。

## 1. Goal

确认扣减 API、状态机、DEDUCT 流水、幂等。

## 2. Repository

repo-1（ai-platform-backend）

## 3. Scope

- mall-inventory: domain.inventory.Inventory 补充 confirmDeduction 方法
- mall-inventory: application.inventory.InventoryApplicationService 补充 confirmDeduction
- mall-inventory: interfaces.rest.internal.InternalInventoryController 补充 confirm 接口

## 4. Design References

- requirement-design.md §2 提议方案
- story-design.md §1 模块改动、§2 接口契约

## 5. Dependencies

DU-BE-403

## 6. Implementation Sketch

```
InternalInventoryController.confirm
  └── InventoryApplicationService.confirmDeduction
        ├── InventoryRepository.findReservationByReservationId
        ├── 若状态已 DEDUCTED → 幂等返回成功
        ├── 若状态非 LOCKED → throw INVALID_STATE
        ├── total -= quantity, locked -= quantity
        ├── reservation.status = DEDUCTED
        ├── save(reservation)
        └── insertLog(DEDUCT)
```

## 7. Pseudocode

命中 complexity-trigger: state-transition（reservation 状态机 LOCKED→DEDUCTED）。

```
confirmDeduction(reservationId):
  reservation = findReservationByReservationId(reservationId)
  if reservation == null: throw NOT_FOUND
  if reservation.status == DEDUCTED: return  // 幂等
  if reservation.status != LOCKED: throw INVALID_STATE
  total -= quantity
  locked -= quantity
  reservation.status = DEDUCTED
  save(reservation)
  insertLog(DEDUCT, skuId, quantity, before=total+quantity, after=total)
```
