# DU Implementation — DU-BE-801

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

### mall-cart：从骨架建成 Redis 写模型服务（8104，无 RDBMS 数据源）

**domain（com.ai.mall.cart.domain.cart）**
- `CartConstants`：`MAX_ITEMS=100`、`MAX_QUANTITY=999`、`TTL_SECONDS=7776000`（90 天）、key 前缀 `cart:member:`
- `CartItem(skuId,quantity,selected,priceFenAtAdded,createdAt,updatedAt)`：Hash field=skuId，value 为本 record JSON；以 design 为准冗余加购时快照价（story-spec §4 的「不存名称价格」指不存商品名称等易变聚合信息）
- `CartErrorCode`：B0301 CART_QUANTITY_LIMIT / B0302 CART_ITEMS_LIMIT / B0303 SKU_NOT_SALABLE / B0304 CART_ITEM_NOT_FOUND / S0301 DEPENDENCY_UNAVAILABLE / S0302 CART_STORAGE_UNAVAILABLE
- `CartScriptCode`：Lua 返回码 0/1/2/3 集中映射；`CartRepository` 端口

**Lua 脚本（resources/scripts/，数据变更与 EXPIRE 同脚本原子完成）**
- `cart_add.lua`：HEXISTS 合并累加（>999 → 1 且保持原值）；HLEN≥100 新 SKU → 2；新条目 selected=true；末尾 EXPIRE
- `cart_update.lua`：HEXISTS 缺失 → 3；数量 1..999 脚本兜底；HSET + EXPIRE
- `cart_remove.lua`：HDEL 单/批幂等；仅 key 仍存在时 EXPIRE（空车不重建 key）
- `cart_select.lua`：SINGLE（缺失 → 3）/ALL 两种模式改 selected + EXPIRE；空车全选 no-op

**infrastructure**
- `RedisCartRepository`：StringRedisTemplate + DefaultRedisScript（类路径加载）；HGETALL 反序列化条目；DataAccessException 原样上抛
- `RestProductSkuClient`：RestClient POST /api/internal/products/skus/batch（默认 `http://localhost:8103`，X-Internal-Token=dev-internal-secret）；响应 @StringId 字符串 ID 做 null 安全解析；任何传输/4xx/5xx/信封异常统一 503 S0301，与业务不可售严格区分；**不含任何 inventory 依赖（AC-014）**
- `CartSecurityConfiguration`（@Profile("!test")）：JWT 资源服务器 + JwtSubjectConverter；/api/internal/** ROLE_SERVICE、/api/mall/** ROLE_MEMBER；401/403 写 UnifyResult 信封；InternalIdentityFilter addFilterBefore

**application / interfaces**
- `CartApplicationService`：add 先批量契约可售校验（product ON_SALE + sku ENABLED + 价格非空）取价后再原子写；不可售 400 车不变；Redis 故障统一 503 S0302；selectedItems 供 M4
- `CartController`（类级 @PreAuthorize MEMBER）：GET /api/mall/cart（本 Story 原始条目视图，DU-BE-802 替换为聚合读模型）、POST /items、PUT /items/{skuId}、DELETE 204 幂等、POST /items/batch-delete、select/unselect/select-all/unselect-all；memberId 仅从 SecurityContextFacade 取（sub=memberId），skuId 线框字符串解析失败 400
- `InternalCartController`：GET /api/internal/carts/members/{memberId}/selected-items → {items:[{skuId,quantity}]}（仅勾选项）

### mall-product：内部 SKU 批量可售快照契约

- `ProductRepository.findBySkuIds`：sku IN → product IN → 图片/属性/SKU 各一次批量装载按 productId 分组（3 轮查询，无 N+1，显式 deleted=0）
- `SkuBatchApplicationService`：≤100、去重保序；缺失 skuId 占位 salable=false；salable = product ON_SALE 且 sku ENABLED；输出价（整数分）/图（sku 主图缺省回退商品主图）/规格/productId/name/双状态
- `POST /api/internal/products/skus/batch`：@Valid 请求体（非空、≤100、正数），ID @StringId 字符串；不触发任何库存查询

### mall-gateway

- 路由 `mall-cart` Path=/api/mall/cart/** → `${MALL_GATEWAY_CART_URI:http://localhost:8104}`
- 鉴权行追加 /api/mall/cart/** → hasRole MEMBER；/api/internal/** denyAll→404 不变（未加 internal cart 路由）

### 工程

- mall-cart pom：移除 mybatis-plus/flyway/mysql（服务无 RDBMS），新增 mall-common-redis、mall-common-security、oauth2-resource-server；test 新增 testcontainers + junit-jupiter（版本由 Boot BOM 托管；surefire 注入 TESTCONTAINERS_RYUK_DISABLED=true）
- application.yml：spring.data.redis（host/port/password 环境变量）、mall.security.jwt.* + internal.shared-secret、mall.cart.product-uri；test profile 排除 DataSourceAutoConfiguration；删除空 db/migration 目录

## Commits

| Hash | 类型 | 说明 |
|------|------|------|
| ebfbeb2 | feat | CHG-0018 DU-BE-801 会员购物车 Redis 写模型（34 files, +1965/-41） |

基线（父提交）：b37c485（CHG-0017 末态）

## Deviations

### DEV-1
- 原 DU 建议: story-spec §4 条目字段列「skuId/quantity/selected/createdAt/updatedAt（不存名称价格）」
- 实际实现: Hash value JSON 含 priceFenAtAdded（加购时刻售价快照，整数分）
- 原因: story-design §1 与 task-design 明确写模型含 priceFenAtAdded（不信任前端价、留痕加购价）；按工作流设计优先序以 design 为准，spec 的「价格」指不存实时价/名称等聚合信息
- 影响评估: 多读模型不直接展示该价（DU-BE-802 实时价另聚合），无价格信任风险；字段随 TTL 90 天自然过期

### DEV-2
- 原 DU 建议: InternalIdentityFilter bean 注册方式待确认
- 实际实现: 确认 `mall-common-security` 已有 `InternalSecurityAutoConfiguration`（@ConditionalOnProperty mall.security.internal.shared-secret），服务仅需配置密钥并在 FilterChain addFilterBefore，与 mall-member 同形
- 原因: 既有跨 Change 机制（CHG-0015/16），无新增代码
- 影响评估: 无

## 自检

- 自动化：mall-cart **23 passed**（RedisCartRepositoryLuaTest 9 例真实 Redis + CartApiTest 13 例全链路 + Smoke 1）；mall-product **95 passed**（基线 93 + 新增 batch 2 例）；mall-gateway **19 passed**
- AC-001 加购默认勾选/快照价/TTL≈7776000 —— Lua addNewItemSetsDefaultsAndTtl；真实环境实测 TTL=7775979
- AC-002 同 SKU 合并、>999 拒绝且保持 —— addMergesSameSku / addOverQuantityLimitKeepsOriginal / mergeAndQuantityLimit
- AC-003 不可售/非法量 → 400 车不变 —— addUnsalableRejected / invalidRequestRejected / product skuBatchSalableAndMissing（DRAFT 双状态）
- AC-004 第 101 个 SKU → 400 保持 100 —— addRejects101stDistinctSku / itemsLimit
- AC-005 改量 404、单删 204/批删幂等 —— updateQuantityLifecycle / removeIsIdempotent / updateAndRemove
- AC-006 单选/全选跨操作保持 —— selectSemantics / selectionLifecycle
- AC-007 401/ADMIN 403/会员隔离、memberId 无入口 —— noTokenUnauthorized / adminForbidden / cartIsolation；真实网关实测无 Token 401
- AC-014 无库存锁定调用 —— mall-cart pom 零 inventory 依赖；ProductSkuClient 仅调 product sku/batch
- 真实环境 E2E（gateway 8080 → cart 8104 → product 8103 → Redis 6379）：注册登录会员 → 加购真实在售 SKU（2099487833061482503，快照价 399900）→ 合并 1+2=3 → DRAFT SKU 400 B0303 → 网关 internal 404 → unselect/select-all 与 M4 selected-items 直连一致
