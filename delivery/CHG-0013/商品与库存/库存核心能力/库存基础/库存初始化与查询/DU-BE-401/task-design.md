# DU Task Design — DU-BE-401

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-401 引用该表，不新造 DU。

## 1. Goal

库存权限码（identity V4）+ Inventory 聚合、inventory_stock/inventory_log 表、初始化与查询 API（单/批/后台）、INIT 流水、SKU 契约校验、安全配置与网关路由。

## 2. Repository

repo-1（ai-platform-backend）

## 3. Scope

- mall-inventory: domain.inventory（Inventory/InventoryLog/InventoryOperationType/InventoryRepository）
- mall-inventory: infrastructure.persistence.inventory（InventoryPo/InventoryLogPo/Mapper/RepositoryImpl）
- mall-inventory: application.inventory（InventoryApplicationService）
- mall-inventory: interfaces.rest.admin（InventoryAdminController + DTO）
- mall-inventory: infrastructure.config（InventorySecurityConfiguration）+ infrastructure.client（SkuClient）
- mall-inventory: Flyway V1（inventory_stock/inventory_log）
- mall-identity: V4__add_inventory_permissions.sql
- mall-gateway: application.yml 路由

## 4. Design References

- requirement-design.md §2 提议方案、§3.1 repo-1、§5 Story 分派、§6 DU 划分
- story-design.md §1 模块改动、§2 接口契约、§3 数据变更

## 5. Dependencies

CHG-0012（mall-product SKU 内部接口）

## 6. Implementation Sketch

```
InventoryAdminController
  └── InventoryApplicationService
        ├── InventoryRepository (findBySkuId/save/insertLog)
        ├── SkuClient (checkSkuExists)
        └── InventoryLog (流水实体)
```

控制流程：
- init: SkuClient.checkSkuExists → InventoryRepository.findBySkuId（已存在抛 CONFLICT）→ Inventory.initialize → save + insertLog(INIT)
- getBySkuId: InventoryRepository.findBySkuId → 返回视图
- batchGet: 批量查询
- page: 分页查询

## 7. Pseudocode

N/A。本 DU 为简单 CRUD 与初始化逻辑，未命中 complexity-trigger（business-flow/algorithm/state-transition/orchestration），核心流程在 §6 Sketch 已表达。
