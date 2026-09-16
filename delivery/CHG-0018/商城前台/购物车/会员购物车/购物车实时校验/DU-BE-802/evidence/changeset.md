# Changeset — DU-BE-802

> 仓库：repo-1（ai-platform-backend），分支 M3-dev。
> 代码提交见 commits.md（feat(cart)，34a0eb2，13 文件）。
> 改动全部落在 mall-cart；product/inventory/gateway 零改动（复用既有内部契约）。

## 1. mall-cart：主干新增

| 文件 | 变更类型 |
|---|---|
| `application/cart/InventoryAvailabilityClient.java` | 新增（库存出站端口 + SkuAvailability；仅可用性查询，无 lock/release/confirm） |
| `infrastructure/client/RestInventoryAvailabilityClient.java` | 新增（POST 8106 availability + X-Internal-Token；故障归一 503；@StringId parseLong 兼容） |
| `application/cart/CartReadModel.java` | 新增（CartLine/CartView + ItemStatus/StockStatus 六/四态枚举；合计口径 Javadoc） |
| `application/cart/CartViewAssembler.java` | 新增（纯函数：双状态优先级、阈值 0/1..9/≥10、调价检测、降级映射、选中合计） |
| `application/cart/CartQueryService.java` | 新增（HGETALL + 各一次批量；空车零调用；双依赖独立 try/catch 降级；Redis 故障 503） |

## 2. mall-cart：主干修改

| 文件 | 变更类型 |
|---|---|
| `interfaces/rest/mall/CartController.java` | 修改（GET 改注入 CartQueryService 返 CartReadView + toLineView；写操作响应不变） |
| `interfaces/rest/mall/dto/CartDtos.java` | 修改（新增 CartLineView/CartReadView；CartItemView 注释改为写操作回显） |
| `domain/cart/CartConstants.java` | 修改（IN_STOCK_THRESHOLD=10，注释对齐 CHG-0017 SSOT） |
| `src/main/resources/application.yml` | 修改（mall.cart.inventory-uri，env INVENTORY_SERVICE_URI，默认 8106） |

## 3. mall-cart：测试新增/修改（+18 例，23→41）

| 文件 | 用例数 | 覆盖 |
|---|---|---|
| `application/cart/CartViewAssemblerTest.java` | 10（新增） | 空车、VALID 回填、四态失效矩阵、调价、阈值 0/1/9/10、缺库存记录按 0、product/inventory/双故障降级、未选中排除 |
| `interfaces/rest/mall/CartReadApiTest.java` | 6（新增） | GET 字段/字符串 ID、七条目矩阵合计 1000、双依赖降级 200、空车零依赖调用、读不改 Redis（Hash+TTL） |
| `infrastructure/CartBoundaryAuditTest.java` | 2（新增） | AC-021：pom 禁用 token 审计；src/main 禁用 import 审计（java.sql/JPA/MyBatis/Spring JDBC） |
| `interfaces/rest/mall/CartApiTest.java` | 13（修改夹具） | 补 @MockitoBean InventoryAvailabilityClient lenient 充足库存；既有用例零回归 |

## 4. 未改动的契约方（只读消费）

- mall-product：`POST /api/internal/products/skus/batch`（DU-BE-801 已落地，本 DU 直接复用）。
- mall-inventory：`POST /api/internal/inventory/availability`（CHG-0017 DU-BE-705 既有，响应精确数量）。
- mall-gateway：/api/mall/cart/** 路由与 MEMBER 鉴权 DU-BE-801 已就位，无需改动。
