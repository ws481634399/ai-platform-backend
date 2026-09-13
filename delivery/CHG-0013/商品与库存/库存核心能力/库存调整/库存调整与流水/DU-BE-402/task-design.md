# DU Task Design — DU-BE-402

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。

## 1. Goal

库存调整领域方法、ADJUST 流水、调整 API。

## 2. Repository

repo-1（ai-platform-backend）

## 3. Scope

- mall-inventory: domain.inventory.Inventory 补充 adjust(delta, reason) 方法
- mall-inventory: application.inventory.InventoryApplicationService 补充 adjust 方法
- mall-inventory: interfaces.rest.admin.InventoryAdminController 补充调整接口 + 流水查询接口

## 4. Design References

- requirement-design.md §2 提议方案
- story-design.md §1 模块改动、§2 接口契约

## 5. Dependencies

DU-BE-401

## 6. Implementation Sketch

```
InventoryAdminController.adjust
  └── InventoryApplicationService.adjust
        ├── InventoryRepository.findBySkuId
        ├── Inventory.adjust(delta, reason)  // 校验 total+delta>=0
        ├── InventoryRepository.save
        └── InventoryRepository.insertLog(ADJUST)
```

## 7. Pseudocode

N/A。调整逻辑为简单领域校验 + 流水记录，未命中 complexity-trigger。
