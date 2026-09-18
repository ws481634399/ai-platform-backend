# DU Task Design — DU-BE-508

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

mall-common-config 统一配置客户端（本地 60s→Redis 600s→HTTP 内部端点→代码默认值，FeatureGate/SystemParameterProvider）；mall-system SERVICE 内部配置端点与写后 Redis 精确失效。

## 2. Repository

repo-1（mall-common/mall-common-config 新模块、mall-services/mall-system）

## 3. Scope

- 新 Maven 模块 mall-common/mall-common-config：
  - pom 依赖 mall-common-core/web（按实际底座）+ mall-common-redis + openfeign（不引 Caffeine）；
  - META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 注册 ConfigClientAutoConfiguration；
  - config.FeatureGate（isEnabled(key)/isEnabled(key,default)/ensureEnabled(key)→B0606 FEATURE_DISABLED 403）；
  - config.SystemParameterProvider（getString/getInt/getLong/getDecimal/getBoolean，必传 default；类型不匹配→default+WARN 限速）；
  - ConfigClient（声明式 Feign 或 RestClient 调 mall-system，keys>100 自动分批，解析 missingKeys）；
  - LocalConfigCache：ConcurrentHashMap\<String,CacheEntry\>（volatile value/empty 标记 + expireAt，TTL 60s，纯 currentTimeMillis 判断，无清理线程）；
  - Redis 读写（StringRedisTemplate + JSON，TTL 600s；空值负缓存 60s）；
  - key 构建 `aimall:{env}:system:feature:{key}`、`...:parameter:{key}`、`...:public-features`。
- mall-system interfaces.rest.internal.InternalConfigController（SERVICE）：
  - GET /api/internal/config/features?keys=（逗号分隔，空/超 100 → B0601）→ {values:{key:{key,enabled,publicFlag}},missingKeys:[]}；
  - GET /api/internal/config/parameters?keys= → {values:{key:{key,configValue,parameterType,minValue,maxValue}},missingKeys:[]}。
- mall-system 缓存失效：配置写提交后 @TransactionalEventListener(AFTER_COMMIT) 删单键；任何 feature 变更恒删 public-features 聚合键。

## 4. Design References

- CHG-0022 requirement-design.md §2.3（缓存三级/键规范/失效）；STORY-006-02-01-01 story-design §1/§2/§4。

## 5. Dependencies

权威表：无。实际前置 DU-BE-507。

## 6. Implementation Sketch

```
消费服务: FeatureGate.isEnabled(key) / Provider.getXxx(key, default)
  → LocalConfigCache.get(key)             // 60s 内（含空标记）直接返回
  → Redis read(key)                       // 命中回填本地
  → ConfigClient.httpGet(keys≤100)        // 分批；missing 入负缓存
        → 回填 Redis(600s)/Local(60s)
  → 全失败/missing: 返回代码默认 + WARN（每分钟每键限一次）

mall-system 写路径 commit
  → CacheEvictionListener: redis.delete(feature:{key}|parameter:{key})
                           redis.delete(public-features)   // 恒删
```

## 7. Pseudocode

```
readFeature(key, fallback=true):
  hit = local.get(key); if hit != null && !hit.expired(now): return hit.valueOrEmpty(fallback)
  redisVal = redis.get(redisKey(FEATURE,key))
  if redisVal != null: local.put(key,parsed); return valueOrEmpty
  resp = configClient.fetchFeatures(chunked([key]))     // Redis/HTTP 异常 → fallback+warn
  v = resp.values[key]
  redis.set(key, v==null ? EMPTY_MARK(ttl60s) : JSON(v)(ttl600s))
  local.put(key, v==null ? empty(ttl60s) : of(v))
  return v?.enabled ?? fallback
```
