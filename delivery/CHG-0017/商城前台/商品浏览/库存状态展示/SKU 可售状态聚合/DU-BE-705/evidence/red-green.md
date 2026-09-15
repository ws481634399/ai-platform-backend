# Red-Green Evidence — DU-BE-705

> SKU 可售状态三态聚合与降级。测试与实现同步编写。

## Green（转绿结果）

| 模块 | 命令 | 结果 |
|---|---|---|
| mall-inventory | `mvn -pl mall-services/mall-inventory test` | 24/24（新增 4 + 既有 20 零回归） |
| mall-product | `mvn -pl mall-services/mall-product test` | 81/81（新增 6 + 既有 75 零回归） |

## TC → 测试映射

| TC | 验证落点 | 结果 |
|---|---|---|
| TC-001 批量精确数量、无记录=0、按顺序 | InternalInventoryAvailabilityApiTest::batchAvailableQty | passed |
| TC-002 product 一次 inventory 调用（无 N+1） | MallSkuAvailabilityApiTest（@MockBean InventoryAvailabilityClient） | passed |
| TC-003 三态映射 0/1-9/≥10 | MallSkuAvailabilityApiTest::threeStateMapping | passed |
| TC-004 白名单 DTO 无数量字段 | MallSkuAvailabilityApiTest::whitelistNoQuantity | passed |
| TC-005 inventory 故障 → UNKNOWN，HTTP 200 | MallSkuAvailabilityApiTest::degradationOnInventoryFailure | passed |
| TC-006 空/>100/非正 400；无 token 401 | 两模块 batchValidation + noTokenReturns401 | passed |
