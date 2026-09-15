# Changeset — DU-BE-705

> 仓库：repo-1（ai-platform-backend），分支 M3-dev。提交 b982bf5。

## 新增文件

| 文件 | 说明 |
|------|------|
| mall-product/interfaces/rest/mall/MallSkuController.java | POST /api/mall/skus/availability |
| mall-product/interfaces/rest/mall/dto/StockStatus.java | 三态枚举 + STOCK_IN_THRESHOLD=10 |
| mall-product/interfaces/rest/mall/dto/MallSkuAvailabilityDtos.java | SkuAvailabilityRequest / SkuAvailabilityView（白名单无数量） |
| mall-product/application/sku/SkuAvailabilityApplicationService.java | 校验 + 一次调用 + 三态映射 + UNKNOWN 降级 |
| mall-product/infrastructure/client/InventoryAvailabilityClient.java | RestClient 直连 inventory 8106，X-Internal-Token |
| mall-product/test/.../mall/MallSkuAvailabilityApiTest.java | 6 例（三态/白名单/降级/校验/id 字符串） |
| mall-inventory/test/.../internal/InternalInventoryAvailabilityApiTest.java | 4 例（批量/401/校验） |

## 修改文件

| 文件 | 说明 |
|------|------|
| mall-inventory/.../InternalInventoryController.java | 新增 POST /api/internal/inventory/availability |
| mall-inventory/.../InternalInventoryDtos.java | AvailabilityRequest / SkuAvailabilityView |
| mall-inventory/.../InventoryApplicationService.java | availabilityMap() 批量查询 |
| mall-inventory/.../InventoryErrorCode.java | AVAILABILITY_BATCH_INVALID(B2208) |
| mall-product/.../ProductErrorCode.java | AVAILABILITY_BATCH_INVALID(B2181) |
| mall-product/resources/application.yml | mall.product.inventory-service-uri |
