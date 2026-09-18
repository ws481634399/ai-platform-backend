# DU Task Design — DU-BE-509

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

功能开关真实消费闭环：public-features 匿名端点（聚合缓存）、mall-search 搜索开关切点 B0606、mall-cart 游客写开关（会员/读不受影响）、网关白名单。

## 2. Repository

repo-1（mall-services/mall-system、mall-search、mall-cart、mall-gateway）

## 3. Scope

- mall-system interfaces.rest.mall.MallConfigController：GET /api/mall/public-features（permitAll）：
  读 Redis 聚合键 public-features（TTL 600s；未命中查 feature_config where public_flag=1 → [{config_key,enabled}] 回填）；仅 key+enabled。
- mall-search：ProductSearchService/MallSearchController 入口 featureGate.ensureEnabled("search.enabled")（缺键默认放行）→ B0606 FEATURE_DISABLED(403)；依赖 mall-common-config。
- mall-cart：游客识别（既有匿名身份机制）写操作入口（加购/改量/删除/选中）featureGate.isEnabled("mall.guest-cart.enabled",true) 为 false 时抛 B0606；会员写、所有读不拦截。
- SearchErrorCode/CartErrorCode 增加 B0606（或集中在 common 定义错误码常量；按工程既有错误码归属实现，HTTP 403）。
- mall-gateway：/api/mall/public-features 加匿名白名单 → mall-system。

## 4. Design References

- CHG-0022 requirement-design.md §2.4（消费闭环/B0606/effectType）；STORY-006-02-01-02 story-design §1/§2/§4。

## 5. Dependencies

权威表：无。实际前置 DU-BE-508。

## 6. Implementation Sketch

```
匿名 GET /api/mall/public-features (gateway permitAll)
 → MallConfigController.publicFeatures()
    → cached(aggregate public-features): repo.findPublicEnabled() → [{key,enabled}]

mall-search.search():
  featureGate.ensureEnabled("search.enabled")   // false → B0606 403
  ...原查询链路

mall-cart 写端点（游客判定）:
  if currentUser is GUEST && !featureGate.isEnabled("mall.guest-cart.enabled", true):
      throw B0606
  // 会员/读操作不受影响
```

## 7. Pseudocode

```
ensureEnabled(key):
  if !isEnabled(key, /*defaultWhenMissing*/ true):
      throw new FeatureDisabledException(B0606, "功能暂未开放")   // 403

guestWriteGuard():
  if securityContext.isGuest():
     if !featureGate.isEnabled("mall.guest-cart.enabled", true):
        throw new FeatureDisabledException(B0606)   // 会员与读路径不进入本方法
```
