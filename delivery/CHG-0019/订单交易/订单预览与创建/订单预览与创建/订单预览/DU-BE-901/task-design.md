# DU Task Design — DU-BE-901

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

mall-order 服务从零骨架到可运行：分层包结构、安全三角色矩阵、四下游 RestClient 端口、Checkout Preview 服务端重算编排、submitToken 签发；mall-member 补一个内部地址端点。

## 2. Repository

repo-1（新增 mall-services/mall-order 8105；mall-member 小改）

## 3. Scope

- mall-order 包 `com.ai.mall.order`：MallOrderApplication；domain/order（Money、OrderErrorCode B04xx、PreviewItem/PreviewResult、StockStatus/IssueCode 常量）；application/order/port（ProductSkuPort、InventoryPort[含 lock/release/confirm 声明]、MemberAddressPort、CartSelectionPort、SubmitTokenStore）；infrastructure/client（Rest*Client，仿 mall-cart RestProductSkuClient：UnifyResult 解包、503 ORDER_DEPENDENCY_UNAVAILABLE、ID Object→parseLong）；infrastructure/redis（RedisSubmitTokenStore + Lua GETDEL 脚本或 execute 原子回调）；infrastructure/config（RestClientConfig、OrderProperties：mall.order.product-uri/inventory-uri/member-uri/cart-uri/internal-token）；interfaces/rest/mall（MemberOrderController#preview、PreviewRequest/View assembler）。
- security：OrderSecurityConfiguration 仿 CartSecurityConfiguration（/api/internal/** SERVICE+denyAll、/api/mall/** MEMBER、/api/admin/** ADMIN）；test ApiTestSecurityConfig。
- mall-member：interfaces/rest/internal 新增 InternalAddressController GET /api/internal/members/{memberId}/addresses/{addressId}；AddressRepository/Impl 已有 findByIdForMember（若无则补双条件 SQL）；不命中抛 AddressErrorCode.NOT_FOUND 经全局异常 → 404。
- yml：server.port 8105；spring.datasource mall_order（H2 test MODE=MySQL）；redis；JWT 公钥路径配置同 mall-cart；flyway enabled、locations classpath:db/migration（空目录占位由 V1 下 DU 补）。

## 4. Design References

- CHG-0019 requirement-design.md §2.1（四世界分层/端口）、§4.1（preview 契约）、错误码 B04xx；STORY-004-01-01-01 story-design.md §1/§2。

## 5. Dependencies

权威表：无。实际前置：CHG-0015（安全/JWT/X-Internal-Token）、CHG-0017（skus/batch 契约）、CHG-0018（cart selected-items）、CHG-0016（inventory availability）。

## 6. Implementation Sketch

- preview 编排：取 memberId（JwtSubjectConverter/既有主体工具）→ 校验 source（CART 时 items 忽略，cartPort.getSelected(memberId) 为空→空结果）；BUY_NOW 校验 1–100 行、qty 1–999 → productPort.batch(skuIds) 返回 Map<skuId,SkuSnapshot>（salable=product ON_SALE && sku ENABLED）→ inventoryPort.availability(skuIds) 返回 Map<skuId,available> → 地址：addressId 存在则 memberPort.findAddress(memberId,addressId)，404 转 invalid 标记（地址故障 5xx 仍 503，"不命中"才 404 语义）→ 逐行组装 PreviewItem（stockStatus：UNKNOWN 表示依赖未返回该 sku；issueCodes 可叠加）→ Money 合计 → 全部无 issue 且地址有效且非空 → tokenStore.issue(memberId, payload{source,addressId,items:[{skuId,quantity}]}，UUID，600s)。
- RedisSubmitTokenStore：issue 用 StringRedisTemplate.opsForValue().set(key, json, Duration.ofSeconds(600))；key `order:submit-token:{memberId}:{token}`；consume 用 DefaultRedisScript<Long/String> GETDEL（Redis 版本兼容：不支持 GETDEL 时退化为 WATCH/MULTI 或 Lua GET+DEL——M4 本地 redis 7，直接 GETDEL）。
- 金额：goodsAmount=Σ 有效单价行 unit*qty；discount/freight 固定 0。
- member 内部地址：直接复用 application 层查询服务（若仅面向 controller repo，新增薄 service 方法），鉴权走 securityFilterChain 的 internal 规则与 X-Internal-Token 过滤器。

## 7. Pseudocode

N/A。编排为直线式端口聚合（拉取→映射→组装→签发），无复杂算法；原子性仅在 Redis GETDEL 一处，用单条 Lua/原语即可。
