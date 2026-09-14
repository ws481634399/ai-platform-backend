# DU Task Design — DU-BE-803

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

游客车合并：mergeToken 签发（SET EX 300 一次性）+ 单 cart_merge.lua 原子校验/合并/删 token（数量相加、999 截断 truncated、100 条目外 dropped、失效条目 dropped）、幂等防重放；公开 POST /api/mall/skus/items 游客车展示快照。

## 2. Repository

repo-1（mall-cart 8104 + mall-product 8103）

## 3. Scope

- mergeToken：`POST /api/mall/cart/merge-token`（MEMBER 鉴权，登录后前端调用）→ 生成 UUID，`SET cart:merge:{token} {memberId} EX 300 NX`；返回 token。
- 合并：`POST /api/mall/cart/merge`（MEMBER 鉴权，body {token, items:[{skuId,quantity,selected,priceFenAtAdded}]}）；单 Lua 脚本 KEYS=[cart:member:{id}, cart:merge:{token}]：GET token 校验存在且值=memberId（不存在/过期 → 400 MERGE_TOKEN_INVALID；不匹配/伪造 → 401）→ DEL token（先删保证幂等：重放必失败且不累加）→ 逐条处理 items。
- Lua 内条目处理：先做存储规则合并（相加 min(999) 截断、HLEN 100 上限）；可售性无法在 Lua 内 HTTP：合并前应用层先调 sku/batch 校验游客条目 → 失效项直接不入脚本并在响应 dropped(reason=SKU_NOT_SALABLE)；有效条目传 Lua；同 SKU 相加超 999 → truncated[]{skuId,finalQuantity}；超 100 的新 sku → dropped(reason=CART_ITEMS_LIMIT)；合并后 EXPIRE 车 7776000。
- token 时序保证：应用层先校验可售（只读），Lua 内完成 token 消费+合并原子；第二次提交 token 已删 → 401/400 不累加。
- 公开 `POST /api/mall/skus/items`（匿名，网关 permitAll）：入 skuIds[]≤100 → 仅返 product ON_SALE 且 sku ENABLED 快照（图/名/规格/价），过滤掉的不返回（前端据缺失键标失效）；三态由前端另行调 CHG-0017 availability 公开端点。

## 4. Design References

- CHG-0018 requirement-design.md §2.4（mergeToken + 单 Lua 原子合并/截断/dropped/幂等）、§4（merge/items 契约与 TTL）；STORY-003-03-02-01 story-design.md §1/§2/§4。

## 5. Dependencies

权威表：无。实际依赖 DU-BE-801（车 Hash/Lua 常量 100/999/90 天、sku/batch）、DU-BE-705（availability）、DU-BE-501。

## 6. Implementation Sketch

- 合并响应：{merged:{skuId,quantity}[], truncated:[{skuId,finalQuantity}], dropped:[{skuId,reason}]}；前端据此提示并清空游客车。
- cart_merge.lua 返回码约定：OK / TOKEN_MISSING / TOKEN_MISMATCH；脚本第一行 GET+DEL 合并（GET 校验后 DEL，单脚本原子）。
- items 端点复用 DU-BE-801 product sku/batch 的应用层查询但走公开 Controller 并做结果过滤（不暴露内部精确库存与下架项）。
- token TTL 与车 TTL 均 Redis TTL 断言验证（300 / 7776000 ±容差）。

## 7. Pseudocode

N/A（metadata `pseudocode: false`）。核心原子性由单 Lua 承担（直线 KEYS/ARGV），应用层仅一次预校验与结果分类。
