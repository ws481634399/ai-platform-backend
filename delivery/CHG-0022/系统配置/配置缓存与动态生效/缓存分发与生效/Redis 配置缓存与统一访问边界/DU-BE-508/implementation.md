# DU Implementation — DU-BE-508

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

建立跨服务配置分发与消费的唯一通道：mall-system 侧 Redis 共享缓存 + SERVICE 内部端点，mall-common 侧新增 mall-common-config 统一客户端。

- **新模块 mall-common/mall-common-config**（注册进 mall-common pom；依赖 mall-common-core/web + mall-common-redis，不引 Caffeine）：
  - [SystemConfigClient.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-common/mall-common-config/src/main/java/com/ai/mall/common/config/SystemConfigClient.java)：三级读「本地 ConcurrentHashMap（volatile 条目 + expireAt，TTL `mall.config.local-ttl-seconds` 默认 60s，纯 currentTimeMillis 判断、无清理线程）→ Redis（StringRedisTemplate + JSON，识别 `{"missing":true}` 负标记，Redis 异常降级）→ HTTP（RestClient 直连 mall-system 8108，带 X-Internal-Token，连接/读超时 1s/3s）」；任一层故障不外抛，以 Optional.empty 表达。
  - [FeatureGate.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-common/mall-common-config/src/main/java/com/ai/mall/common/config/FeatureGate.java)：isEnabled(key)/isEnabled(key,default)、ensureEnabled 两重载；缺键/链路故障按调用方默认值决策并 WARN，仅显式 `false` 抛 FeatureDisabledException（B0606/403）。
  - [SystemParameterProvider.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-common/mall-common-config/src/main/java/com/ai/mall/common/config/SystemParameterProvider.java)：getString/getInt/getLong/getDecimal/getBoolean 必传默认值；缺键/类型不符回退默认；WARN 每键每分钟限一条。
  - [ConfigErrorCode.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-common/mall-common-config/src/main/java/com/ai/mall/common/config/ConfigErrorCode.java) 冻结 B0601~B0604 + B0606；[FeatureDisabledException.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-common/mall-common-config/src/main/java/com/ai/mall/common/config/FeatureDisabledException.java)（RuntimeThrowable，模块自持语义）；[ConfigClientAutoConfiguration.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-common/mall-common-config/src/main/java/com/ai/mall/common/config/ConfigClientAutoConfiguration.java)（`mall.config.enabled` 缺省装配，注册三 Bean + FeatureDisabledException→403/B0606 全局 advice）经 AutoConfiguration.imports 生效。
- **mall-system 缓存与内部端点**：
  - [ConfigCacheService.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/infrastructure/cache/ConfigCacheService.java)：mall-system 侧唯一 Redis 读写者。featureSnapshots/parameterSnapshots 按「槽位」三态（无结论→回源 DB 并回填 / missing 负标记 TTL 60s / found 值 TTL 600s）工作；publicFeatures 读聚合键，未命中查 public_flag=1（含禁用项）回填 TTL 600s；Redis 读/写异常均 catch 降级不外抛。冻结键：`aimall:{env}:system:feature:{key}`、`aimall:{env}:system:parameter:{key}`、`aimall:{env}:system:public-features`；Redis JSON 短名 value/type。
  - [CacheEvictionListener.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/infrastructure/cache/CacheEvictionListener.java)：`@TransactionalEventListener(AFTER_COMMIT)` 同步删单键 + 恒删聚合键（DU-BE-507 应用服务写库即发 ConfigChangedEvent）。
  - [InternalConfigController.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/interfaces/rest/internal/InternalConfigController.java)：GET `/api/internal/config/features`、`/parameters?keys=`（逗号分隔，必填、去重、≤100，否则 400）；响应 `{values:{key:{...}},missingKeys:[...]}`；HTTP 视图全名 [ParameterCacheView](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/infrastructure/cache/ParameterCacheView.java)（configValue/parameterType/minValue/maxValue），FeatureCacheView 为 {key,enabled,version}。
- **消费方接入**：mall-search、mall-cart pom 引入 mall-common-config（两服务 src 内 grep `mall_system`/`mall-system` 0 命中，无配置库数据源/DAO/Mapper）；未显式覆写 mall.config.*，按缺省 baseUri=http://localhost:8108、token=dev-internal-secret、本地 TTL 60s 装配。
- **测试**：[SystemConfigClientTest.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-common/mall-common-config/src/test/java/com/ai/mall/common/config/SystemConfigClientTest.java) 7 例（JDK HttpServer 零依赖桩 + mock Redis）、[ConfigFacadesTest.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-common/mall-common-config/src/test/java/com/ai/mall/common/config/ConfigFacadesTest.java) 5 例；mall-system 侧 [ConfigCacheIntegrationTest.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/test/java/com/ai/mall/system/cache/ConfigCacheIntegrationTest.java) 8 例（testcontainers 真实 Redis 7.4.11 + H2）。

## Commits

| Commit | 仓库 | 说明 |
| --- | --- | --- |
| 82ccf6e | repo-1 | M5 三 Change 合并提交，含本 DU 全部内容：mall-common-config 新模块（三级读客户端/Gate/Provider/AutoConfiguration）、mall-system ConfigCacheService/槽位视图/CacheEvictionListener/InternalConfigController |

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
           - 原 DU 建议:
           - 实际实现:
           - 原因:
           - 影响评估: -->

### DEV-1
- 原 DU 建议: 键前缀中 env 取 `@Value("${spring.profiles.active:dev}")` 占位解析（story-design §1 冻结键规范时的写法）。
- 实际实现: mall-system ConfigCacheService 与 mall-common-config ConfigClientAutoConfiguration 统一改为注入 `Environment` 取 `getActiveProfiles()[0]`，无激活 profile 时回落 `dev`。
- 原因: `@Value` 占位在测试切片等部分装配路径下可能无法解析；Environment API 在任何 ApplicationContext 中均可用，两侧实现严格一致避免键空间错配。
- 影响评估: 生产/测试键命名行为与冻结规范完全一致（aimall:{env}:system:...），测试固定 env=test 已断言真实键名与 TTL；仅实现手段变化。

### DEV-2
- 原 DU 建议: DU Pseudocode 中客户端 HTTP 回源成功后由消费方回写 Redis（值 TTL 600s、空标记 60s）。
- 实际实现: 客户端 HTTP 层不写 Redis；所有 Redis 回填（含负标记）均由 mall-system 读路径在服务端完成（ConfigCacheService 回源 DB 后写键），客户端只读。
- 原因: 保持「mall-system 是共享 Redis 配置命名空间的唯一写者」，避免多消费服务序列化口径漂移；内部端点响应本身就来自服务端缓存装配，回填在请求处理内顺带完成。
- 影响评估: 读路径结果等价（HTTP 200 返回时 Redis 必已回填），SystemConfigClientTest.redisMissFallbackToHttp 与 ConfigCacheIntegrationTest S2-TC-002 双向验证；客户端少持 Redis 写权限语义，边界更干净。

### DEV-3
- 原 DU 建议: ConfigClient 提供多键批量查询，keys>100 自动分批并解析 missingKeys。
- 实际实现: 对外门面为单键 API（getFeature(key)/getParameter(key)），HTTP 逐键请求；>100 分批逻辑未实现。服务端内部端点保留 keys 必填/去重/≤100 校验与 missingKeys 契约。
- 原因: M5 两个消费切点（search.enabled、mall.guest-cart.enabled）与 Provider 均为单键读取，无批量消费场景；分批为无调用方的预留复杂度。
- 影响评估: 高频多键场景下 HTTP 调用数偏多，但每键有 60s 本地缓存兜底；后续出现批量调用方时可在不破坏单键 API 的前提下增补。

### DEV-4
- 原 DU 建议: task-design 未明确 AFTER_COMMIT 删 Redis 失败时的异常策略。
- 实际实现: ConfigCacheService.evict catch 全部异常，error→warn（「配置缓存失效失败…」）不抛出，监听器不影响事务提交结果；由 TTL 600s 兜底最终收敛。
- 原因: 缓存失效失败不应让已提交的配置变更回滚或向管理员报错；测试/无 Redis 环境也走同一路径（ConfigAdminApiTest 不依赖 Redis 可用）。
- 影响评估: Redis 故障期间最长有 600s 旧值窗口（正常删键 + 60s 本地 TTL 设计上限不变），故障恢复后随 TTL 自然收敛；与「写路径只删不写」的容错模型一致。

### DEV-5
- 原 DU 建议: story-spec §2.1「mall-system pom 接入 mall-common-redis」。
- 实际实现: mall-system 直接依赖官方 `spring-boot-starter-data-redis`，未引 mall-common-redis 中转模块。
- 原因: 工程 design.md §2.7 约定数据访问类 starter 由服务直连、不经 common 中转；StringRedisTemplate 自动配置即可满足。
- 影响评估: Redis 连接配置项（spring.data.redis.*）与其他服务一致，键空间约定由本 DU 代码自持；无功能差异。

## 自检

对照 task-spec.md「Verification」逐条：

- **Unit（三级读顺序/回填/负缓存/分批/限速 WARN/故障回退，Fake Redis + WireMock）**：✅ SystemConfigClientTest 7 例——redisHitThenLocalCache（Redis 命中 + 二次读零回源）、redisNegativeMarkerCached（负标记本地缓存不透穿）、redisMissFallbackToHttp（HTTP 回源 + X-Internal-Token 头）、httpParameterFullFieldNames（configValue/parameterType 全名兼容）、redisFailureAndHttpMissing（Redis 异常 + missingKeys → empty）、allLayersDown（全链路故障不抛）、localCacheExpires（TTL=1s 到期重新回源）；ConfigFacadesTest 5 例——Gate 显式 false 抛异常带 key、显式 true 放行、缺键默认与显式保守默认、Provider 五类型解析与错值回退、错误码冻结断言。差异：HTTP 桩用 JDK HttpServer 而非 WireMock；WARN 限速（warnOnce 每分钟每键）有实现但未做日志断言；分批见 DEV-3。
- **Integration（testcontainers Redis 真实 TTL；AFTER_COMMIT 删键后再读新值）**：✅ ConfigCacheIntegrationTest 8 例——S2-TC-002 回源回填并断言 TTL ∈ [500,600]、S2-TC-003 旁路改库后二次读仍为缓存旧值、S2-TC-004 负标记 `{"missing":true}` TTL ∈ [1,60]、S2-TC-007 管理端 PUT 提交后单键与 public-features 聚合键被删、再读回源得 false 新值。
- **API（MockMvc 401/403/400/200/missingKeys）**：✅ S2-TC-001 无内部身份 4xx；S2-TC-005 空 keys（空白串）与 101 键均 400；S2-TC-004/S2-TC-006 missingKeys 正确；S2-TC-006 参数快照 configValue/parameterType/min/max 字段齐全。「经网关 404」由网关 `/api/internal/**` denyAll 配置承载（既有网关测试基线），本 DU 未新增网关自动化用例。
- **Migration**：N/A ✅ 本 DU 无 DDL（运行期新增 Redis 命名空间）。
- **Error Case（Redis 停、HTTP 5xx、keys 超 100、类型错配）**：✅ Redis 异常（redisFailureAndHttpMissing/allLayersDown）、keys 超 100（S2-TC-005）、类型错配（ConfigFacadesTest.providerFallbacks 坏 INTEGER 回退默认）；HTTP 5xx 与连接失败同走 RestClient catch→empty 路径（allLayersDown 以不可达端口覆盖超时/连接异常，未单独桩 500 响应）。
- **静态门禁（TC-007 消费服务无 mall_system 数据源/DAO）**：✅ mall-search、mall-cart 源码 grep `mall_system|mall-system` 0 命中；两服务仅在 pom 声明 mall-common-config 依赖。
- **测试计数（源码逐方法核实）**：SystemConfigClientTest 7、ConfigFacadesTest 5、ConfigCacheIntegrationTest 8，共 20 例。本次为文档回填，未重新执行 Maven 测试（缓存 IT 依赖 Docker 运行 Redis 容器）。
