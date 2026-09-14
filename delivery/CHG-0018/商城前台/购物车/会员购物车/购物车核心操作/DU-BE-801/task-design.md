# DU Task Design — DU-BE-801

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

会员购物车 Redis 写模型全套：Lua 原子写 + 滑动 TTL 90 天、加购可售校验（product internal sku/batch 新契约）、条目/数量上限、改量/删除/选择、归属安全、M4 预留 selected-items。

## 2. Repository

repo-1（mall-cart 8104 骨架补齐 + mall-product 8103 新 internal 端点 + mall-gateway）

## 3. Scope

- mall-cart：pom 增 mall-common-redis/mall-common-security；RedisConfig（StringRedisTemplate，默认序列化）；scripts/cart_add.lua、cart_update.lua（或合并单脚本按 op 分派）；CartRepository（Redis Hash 操作）；CartAppService + MemberCartController（/api/mall/cart：GET 放 DU-BE-802、POST items、PUT items/{skuId}、DELETE items/{skuId}、DELETE items、PUT items/select、PUT select-all）。
- key：`cart:member:{memberId}` Hash<skuId, JSON{quantity,selected,priceFenAtAdded,createdAt,updatedAt}>；TTL 7776000，每次写 EXPIRE 续期（Lua 内 PEXPIRE/EXPIRE）。
- 常量：MAX_ITEMS=100（HLEN）、MAX_QUANTITY=999；超限 400 CART_ITEMS_LIMIT / QUANTITY_LIMIT（加购 1000 拒且保持 999）。
- 加购前调 product `POST /api/internal/products/skus/batch`（≤100，X-Internal-Token）：返商品 ON_SALE + SKU ENABLED 双状态、salePriceFen、图、spec；不满足 → 400 SKU_NOT_SALABLE；不查/不锁库存。
- product 新 internal：SkuBatchController/AppService，一次查 product+sku 双状态快照；无凭证 401。
- 安全：memberId 仅取主体；请求体 memberId 字段忽略；无 Token 401；ADMIN Token 调 /api/mall/** 403（网关矩阵 DU-BE-602）。
- M4 预留：`GET /api/internal/carts/members/{id}/selected-items`（凭证校验，返选中条目快照；M3 无业务调用方）。

## 4. Design References

- CHG-0018 requirement-design.md §2.1（Redis Hash/Lua/TTL 方案）、§2.3（可售校验不锁库存）、§4（cart 与 sku/batch 契约、错误码）；STORY-003-03-01-01 story-design.md §1/§2/§4。

## 5. Dependencies

权威表：无。实际前置：DU-BE-501（凭证/@StringId/网关）、CHG-0017 DU-BE-704（SKU 数据模型与状态口径）；读车模型在 DU-BE-802。

## 6. Implementation Sketch

- cart_add.lua：if HEXISTS → q=旧+新；if q>999 return {errCode=Q}；else HSET 更新；else if HLEN>=100 return {errCode=L}；HSET 新条目（selected=true 默认，priceFenAtAdded 由应用层先查价后传入脚本）；end；EXPIRE key 7776000；返回新数量。脚本纯校验存储规则（可售性在应用层先查，避免 Lua 内 HTTP）。
- 改量：q∈1..999 整数；删除：HDEL（单/批，批删不存在幂等）；选择：HGETALL→改 selected 字段→HMSET/HSET 回写（字段级脚本或事务），每次写续 TTL。
- JSON 字段用固定小 DTO 序列化；时间 epoch millis。
- 加购响应返回写入后的条目（供前端直接刷新；完整读模型由 GET 聚合提供）。
- 审计边界：mall-cart 无数据源 JDBC、无 inventory lock 依赖（pom/import 检查）。

## 7. Pseudocode

N/A（metadata `pseudocode: false`）。原子性全部下沉 Lua（单脚本 KEYS/ARGV 直线），应用层为参数校验+单次调用，无复杂分支算法。
