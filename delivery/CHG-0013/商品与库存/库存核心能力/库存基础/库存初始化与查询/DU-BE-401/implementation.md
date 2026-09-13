# DU Implementation — DU-BE-401

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。

## 变更内容

新建 mall-inventory 服务库存核心能力：库存初始化与查询。库存作为独立限界上下文，不与商品共享表结构。

### 领域层

- `domain/inventory/Inventory.java`（新增）：聚合根。`initialize(skuId, totalQuantity)` 校验 totalQuantity >= 0，创建库存记录（locked=0）；`getAvailableQuantity() = totalQuantity - lockedQuantity`。
- `domain/inventory/InventoryRepository.java`（新增）：端口。`findBySkuId`、`findBySkuIds`、`page`、`insert`、`update`、`insertLog`。
- `domain/inventory/InventoryLog.java`（新增）：流水实体。
- `domain/inventory/InventoryException.java` / `InventoryErrorCode.java`（新增）：B2201 库存不存在、B2202 已存在、B2203 数量非法、B2205 SKU 不存在。

### 应用层

- `application/inventory/InventoryApplicationService.java`（新增）：`init(skuId, totalQuantity)` 经 SkuClient 校验 SKU 存在 → 查重 → Inventory.initialize → insert + insertLog(INIT)。`getBySkuId`、`batchGet`、`page`。

### 接口层

- `interfaces/rest/admin/InventoryAdminController.java`（新增）：`GET /api/admin/inventory/stocks`（分页）、`GET /{skuId}`（详情）、`POST /batch`（批量）、`POST /stocks/init`（初始化），权限码 `inventory:stock:list/detail/init`。

### 基础设施

- `infrastructure/persistence/inventory/InventoryRepositoryImpl.java`（新增）：MyBatis-Plus 实现。
- `infrastructure/client/SkuClient.java`（新增）：调用 mall-product `/api/internal/products/skus/{skuId}` 校验 SKU。
- `infrastructure/config/InventorySecurityConfiguration.java`（新增）：JWT 资源服务器，`/api/admin/**` 需 ADMIN 角色。
- `db/migration/V1__create_inventory_stock_and_log.sql`（新增）：inventory_stock（sku_id 唯一）+ inventory_log 表。
- mall-product：新增 `existsBySkuId` 仓储方法与 `/api/internal/products/skus/{skuId}` 内部接口。
- mall-identity：V6 迁移添加 inventory:stock:\* 权限点与库存菜单。
- mall-gateway：添加 `/api/admin/inventory/**`、`/api/internal/inventory/**` 路由到 8106。

## Commits

| Commit  | DU                    | 消息                                           |
| ------- | --------------------- | ---------------------------------------------- |
| 08d619f | DU-BE-401/402/403/404 | feat(inventory): 库存核心领域模型与持久化层    |
| e2bff4b | DU-BE-401/402/403/404 | feat(inventory): 库存应用服务与管理端/内部接口 |
| eacca66 | DU-BE-401/402/403/404 | feat: 库存跨服务支持与权限路由                 |

## Deviations

无。

## 交付后联调补全（2026-09-13，未提交）

- V6 迁移修复：超管菜单授权补 `/inventory` 目录（原仅授两个 PAGE，菜单树不装配目录），已重跑迁移验证。
- `InventorySecurityConfiguration` 由 JwtSubjectConverter 切换为 RedisSnapshotAuthorityConverter + application.yml 增加 spring.data.redis 只读配置，修复管理端库存接口在真实链路必 403 的问题（JWT 不含权限 claim，权限经共享 Redis 授权快照获取）。`/api/internal/**` 维持 permitAll。
- 实测：GET /api/admin/inventory/stocks、/logs 经网关均 200，无 Token 401；模块测试全绿。
- 公共修复同时惠及 DU-BE-402/403/404 的管理端接口；详细记录见 workspace 仓 CHG-0013 implementation.md §4。

## 自检

- [x] SKU 不存在时初始化返回 400（B2205）
- [x] 库存已存在时初始化返回 409（B2202）
- [x] 初始化后 locked=0，available=total
- [x] 分页/详情/批量查询可用
- [x] 库存与商品独立上下文，product_spu/product_sku 无 stock 字段
