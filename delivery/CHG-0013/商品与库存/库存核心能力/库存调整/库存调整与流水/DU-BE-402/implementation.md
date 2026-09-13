# DU Implementation — DU-BE-402

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。

## 变更内容

库存调整（正入库/负出库）与库存流水查询。

### 领域层

- `domain/inventory/Inventory.java`：`adjust(delta)` 校验调整后 totalQuantity >= 0，更新 totalQuantity。
- `domain/inventory/InventoryOperationType.java`（新增）：枚举 INIT/ADJUST/LOCK/RELEASE/DEDUCT。

### 应用层

- `application/inventory/InventoryApplicationService.java`：`adjust(skuId, delta, reason, businessId)` → 加载库存 → adjust → update + insertLog(ADJUST)。`logs(query)` 分页查询流水。

### 接口层

- `interfaces/rest/admin/InventoryAdminController.java`：`POST /stocks/{skuId}/adjust`（调整），权限码 `inventory:stock:adjust`；`GET /logs`（流水列表），权限码 `inventory:log:list`。

## Commits

| Commit | DU | 消息 |
| --- | --- | --- |
| 08d619f | DU-BE-401/402/403/404 | feat(inventory): 库存核心领域模型与持久化层 |
| e2bff4b | DU-BE-401/402/403/404 | feat(inventory): 库存应用服务与管理端/内部接口 |

## Deviations

无。

## 自检

- [x] 正数调整增加库存，负数调整减少库存
- [x] 调整后总库存为负时拒绝（B2203）
- [x] 调整操作写入 inventory_log 流水（operationType=ADJUST）
- [x] 流水列表可按 skuId 过滤、分页
