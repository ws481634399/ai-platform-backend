# DU Implementation — DU-BE-705

> DU 级实施记录（Actual Implementation）。

## 变更内容

### inventory 内部端点（POST /api/internal/inventory/availability）
- `InternalInventoryController.availability()`：body `{skuIds:[]≤100}`，X-Internal-Token 鉴权；校验空/>100/非正 → 400 AVAILABILITY_BATCH_INVALID
- `InventoryApplicationService.availabilityMap(skuIds)`：一次 `findBySkuIds` 批量 SQL，返回 skuId→availableQty(total-locked) 映射；无记录不入 map（调用方补 0）
- `InternalInventoryDtos`：`AvailabilityRequest(skuIds)`、`SkuAvailabilityView(@StringId skuId, availableQty)`（精确数量，仅内部可达）
- `InventoryErrorCode.AVAILABILITY_BATCH_INVALID`（B2208）

### product 公开端点（POST /api/mall/skus/availability）
- `MallSkuController.availability()`：匿名，body `{skuIds:[]≤100}`
- `SkuAvailabilityApplicationService.availability()`：校验 → `InventoryAvailabilityClient` 一次调用 → 三态映射；异常 → 全部 UNKNOWN（HTTP 200）
- `StockStatus` 枚举 + `STOCK_IN_THRESHOLD=10`：`fromAvailableQty(qty)`：0→OUT_OF_STOCK，1-9→LOW_STOCK，≥10→IN_STOCK；UNKNOWN 为降级态
- `MallSkuAvailabilityDtos`：`SkuAvailabilityRequest`、`SkuAvailabilityView(@StringId skuId, stockStatus)`（白名单：无数量字段）
- `InventoryAvailabilityClient`：RestClient 直连 8106，X-Internal-Token，POST `/api/internal/inventory/availability`
- `ProductErrorCode.AVAILABILITY_BATCH_INVALID`（B2181）
- `application.yml`：`mall.product.inventory-service-uri`

### 网关
- `/api/mall/skus/**` 已在 DU-BE-702 加入 permitAll；`/api/internal/inventory/**` 既有 denyAll

## Commits

| Hash | 类型 | 说明 |
|------|------|------|
| b982bf5 | feat | SKU 可售状态三态聚合与降级 |

## Deviations

无。

## 自检

- TC-001：批量精确数量、无记录=0、按顺序 —— InternalInventoryAvailabilityApiTest::batchAvailableQty
- TC-002：product 一次 inventory 调用（@MockBean 断言无 N+1）
- TC-003：三态映射 0/1-9/≥10 —— MallSkuAvailabilityApiTest::threeStateMapping
- TC-004：白名单 DTO 无数量字段 —— MallSkuAvailabilityApiTest::whitelistNoQuantity
- TC-005：inventory 故障 → UNKNOWN，HTTP 200 —— MallSkuAvailabilityApiTest::degradationOnInventoryFailure
- TC-006：空/>100/非正 400；无 token 401 —— 两模块校验测试
- inventory 24/24、product 81/81，零回归
