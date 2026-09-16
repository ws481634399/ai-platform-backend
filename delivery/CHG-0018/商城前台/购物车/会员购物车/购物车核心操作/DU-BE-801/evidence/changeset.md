# Changeset — DU-BE-801

> 仓库：repo-1（ai-platform-backend），分支 M3-dev。
> 代码提交见 commits.md（feat(cart)，ebfbeb2，34 文件 +1965/-41）。
> 涉及 mall-cart（新服务业务化）、mall-product（新增内部契约）、mall-gateway（路由鉴权）三模块。

## 1. mall-cart：主干新增

| 文件 | 变更类型 |
|---|---|
| `domain/cart/CartConstants.java` | 新增（MAX_ITEMS=100、MAX_QUANTITY=999、TTL_SECONDS 90 天、memberKey） |
| `domain/cart/CartItem.java` | 新增（写模型 record：skuId/quantity/selected/priceFenAtAdded/createdAt/updatedAt） |
| `domain/cart/CartErrorCode.java` | 新增（B0301~B0304 / S0301 / S0302） |
| `domain/cart/CartScriptCode.java` | 新增（Lua 返回码 0/1/2/3 枚举集中映射） |
| `domain/cart/CartRepository.java` | 新增（仓储端口：add/updateQuantity/remove/removeBatch/selectOne/selectAll/findItems） |
| `resources/scripts/cart_add.lua` | 新增（合并累加>999→1；HLEN≥100→2；新条目 selected=true；EXPIRE） |
| `resources/scripts/cart_update.lua` | 新增（HEXISTS→3；数量兜底 1..999；HSET+EXPIRE） |
| `resources/scripts/cart_remove.lua` | 新增（HDEL 单/批幂等；key 存活才续期） |
| `resources/scripts/cart_select.lua` | 新增（SINGLE/ALL 勾选；缺失→3；空车 no-op） |
| `infrastructure/redis/RedisCartRepository.java` | 新增（StringRedisTemplate + DefaultRedisScript；HGETALL JSON 反序列化） |
| `infrastructure/client/RestProductSkuClient.java` | 新增（POST product sku/batch + X-Internal-Token；故障统一 503 S0301；ID 字符串兼容解析） |
| `infrastructure/config/CartSecurityConfiguration.java` | 新增（@Profile("!test")：JWT 资源服务器 + InternalIdentityFilter；MEMBER/SERVICE 路径收口） |
| `application/cart/ProductSkuClient.java` | 新增（出站端口 + SkuSnapshot record） |
| `application/cart/CartApplicationService.java` | 新增（加购先双状态校验取价再原子写；上限/404/503 映射；selectedItems） |
| `interfaces/rest/mall/CartController.java` | 新增（写接口全集 + GET 原始车视图；类级 MEMBER；memberId 仅取安全上下文） |
| `interfaces/rest/mall/dto/CartDtos.java` | 新增（请求/视图 record；Bean Validation；雪花 ID 字符串） |
| `interfaces/rest/internal/InternalCartController.java` | 新增（M4 selected-items，仅 SERVICE 凭证） |

## 2. mall-cart：工程与配置

| 文件 | 变更类型 |
|---|---|
| `pom.xml` | 修改（移除 mybatis-plus/flyway/mysql；新增 mall-common-redis/mall-common-security/oauth2-resource-server；test 加 testcontainers；surefire 关 Ryuk） |
| `src/main/resources/application.yml` | 修改（删数据源/Flyway/MyBatis；加 Redis/安全/internal/product-uri） |
| `src/test/resources/application-test.yml` | 修改（排除 DataSourceAutoConfiguration） |
| `src/main/resources/db/migration/.gitkeep` | 删除（无 RDBMS 迁移目录） |

## 3. mall-cart：测试新增（22 例新增 + Smoke 改造）

| 文件 | 用例数 | 覆盖 |
|---|---|---|
| `infrastructure/redis/RedisCartRepositoryLuaTest.java` | 9 | 真实 Redis Lua：默认值/TTL、合并、>999 保持、101 条边界、改量 404/兜底、删除幂等、勾选语义、滑动续期、会员隔离 |
| `interfaces/rest/mall/CartApiTest.java` | 13 | 401/403/internal 凭证、加购主链、不可售 400、503、参数 400、合并上限、条目上限、改删选全链、归属隔离、M4 selected-items |
| `support/AbstractRedisIntegrationTest.java` | 基建 | Redis 7 Testcontainer 单例 + DynamicPropertySource |
| `support/ApiTestSecurityConfig.java` | 基建 | 进程内 RSA 测试安全链（JWT + InternalIdentityFilter） |
| `MallCartApplicationSmokeTest.java` | 1（改造） | 上下文装配（无数据源、Redis 懒连接） |

## 4. mall-product：内部 sku/batch 契约

| 文件 | 变更类型 |
|---|---|
| `domain/product/ProductRepository.java` | 修改（新增 findBySkuIds 端口） |
| `infrastructure/persistence/product/ProductRepositoryImpl.java` | 修改（批量装配：sku IN→product IN→图/属性/SKU 三次 IN 分组，无 N+1，显式 deleted=0） |
| `application/sku/SkuBatchApplicationService.java` | 新增（≤100 去重保序；缺失占位 salable=false；双状态判定；SkuBatchItem record） |
| `interfaces/rest/internal/dto/SkuBatchDtos.java` | 新增（SkuBatchRequest 校验 + SkuBatchItemView，ID @StringId） |
| `interfaces/rest/internal/InternalProductController.java` | 修改（POST /skus/batch 端点） |
| `test/.../internal/InternalProductApiTest.java` | 修改（+2 例：DRAFT/ON_SALE 双状态+缺失占位+字段完整性；凭证/空列表校验） |

## 5. mall-gateway

| 文件 | 变更类型 |
|---|---|
| `security/GatewaySecurityConfiguration.java` | 修改（MEMBER 行追加 /api/mall/cart/**） |
| `src/main/resources/application.yml` | 修改（mall-cart 路由 → 8104，支持 MALL_GATEWAY_CART_URI 覆写） |
