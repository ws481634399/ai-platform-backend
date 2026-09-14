# DU Task Design — DU-BE-705

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

可售状态聚合：inventory 内部批量 availability（一次 SQL 精确数量）+ product 公开三态端点（阈值常量 10，白名单字段，UNKNOWN 降级），消除 N+1 且浏览器不接触精确库存。

## 2. Repository

repo-1（mall-inventory 8106 + mall-product 8103）

## 3. Scope

- mall-inventory：internal `POST /api/internal/inventory/availability`，body {skuIds:[]≤100}，X-Internal-Token（DU-BE-501）；Mapper 一次批量：`SELECT sku_id, available_qty FROM stock WHERE sku_id IN (...)`（无记录行语义 available=0）；响应精确数字（仅内部可达）。
- mall-product：公开 `POST /api/mall/skus/availability`，body {skuIds:[]≤100}（匿名）；InventoryAvailabilityClient（RestClient 直连 8106，X-Internal-Token，一次调用，无 N+1）；三态映射：available=0→OUT_OF_STOCK；1..9→LOW_STOCK；≥10→IN_STOCK（常量 STOCK_IN_THRESHOLD=10）；inventory 5xx/超时/部分失败 → 对应条目（或全部）UNKNOWN，公开端点 HTTP 仍 200。
- 校验：空数组、>100、含非法（非数字/负数）id → 400 AVAILABILITY_BATCH_INVALID。
- 公开响应白名单 DTO：仅 {skuId(字符串), stockStatus}，禁止数量字段（用独立 record 物理隔离）。

## 4. Design References

- CHG-0017 requirement-design.md §2.5（三态与降级、阈值 SSOT）、§4（两 availability 契约）；STORY-003-02-03-01 story-design.md §1/§2/§4。
- 内部调用先例：SkuClient（RestClient 直连服务端口）。

## 5. Dependencies

权威表：无。实际依赖 DU-BE-501（内部凭证/DTO @StringId/网关：公开路径放行 + internal denyAll）。

## 6. Implementation Sketch

- product 控制器收单 → 校验 → Client 一次 POST → 对返回集逐条阈值映射；inventory 未返回的 skuId（缺失键）按 0 处理（OUT_OF_STOCK）；调用整体异常 → 全条目 UNKNOWN；可区分 partial：接口本身只返回查到的行，缺失键=0 不是失败，仅在 RestClient 异常时 UNKNOWN。
- inventory 现有 stock 表与批量先例（admin /stocks/batch）参考；不锁库存、不改库存（纯读）。
- 网关：/api/mall/skus/availability 加入 permitAll；/api/internal/inventory/** 经 denyAll 仅集群内可达。

## 7. Pseudocode

N/A（metadata `pseudocode: false`）。一次批量读 + 常量阈值映射，无算法。
