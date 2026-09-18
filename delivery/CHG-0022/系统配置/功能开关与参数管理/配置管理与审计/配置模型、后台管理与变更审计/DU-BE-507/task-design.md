# DU Task Design — DU-BE-507

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

mall-system 配置管理垂直：feature_config/system_parameter/system_config_history 三表 V1+种子、CRUD/启停/类型校验/乐观锁/审计留痕、三类 admin 端点、identity V10 权限菜单、网关路由。

## 2. Repository

repo-1（mall-services/mall-system 新服务、mall-identity、mall-gateway）

## 3. Scope

- mall-system 骨架（沿用既有服务脚手架：启动类/common 依赖/端口 8108/MyBatis-Plus/security/redis）。
- V1__init_system_config.sql：
  - feature_config(id,config_key UK,feature_name,config_group,enabled,public_flag,built_in,version default 0,description,created_at,updated_at)；
  - system_parameter(id,config_key UK,parameter_name,config_group,parameter_type,config_value,default_value,min_value,max_value,effect_type,built_in,version,description,created_at,updated_at)；
  - system_config_history(id,config_type[FEATURE|PARAMETER],config_key,old_value,new_value,change_reason,operator,created_at, idx(config_type,config_key,id))；
  - 种子 4 键：search.enabled=true/public、mall.guest-cart.enabled=true/public、search.default-page-size=20(INTEGER,1..100,DYNAMIC)、cart.max-item-quantity=99(INTEGER,1..999,DYNAMIC)；幂等（INSERT ... 不存在时）。
- domain.config：FeatureConfig/SystemParameter/ConfigHistory + ParameterType(STRING/INTEGER/LONG/DECIMAL/BOOLEAN/JSON)、EffectType(DYNAMIC/RESTART_REQUIRED)。
- application.config：FeatureConfigApplicationService/SystemParameterApplicationService/ConfigHistoryQueryService：
  CAS update（where id,version → 0 行 B0604）；builtIn 禁删/禁改 key；类型+范围校验（失败 B0601）；UK 冲突 B0602；不存在 B0603；任何值变更/启停 AFTER 写 history（同事务）。
- interfaces.rest.admin：/api/admin/feature-configs、/api/admin/system-parameters（list/page/group、create、update、enable-disable、delete）、/api/admin/config-history（type/key 过滤分页，只读）；@PreAuthorize 权限码。
- 错误码：B0601(400)/B0602(409)/B0603(404)/B0604(409)。
- mall-identity V10__system_config_permissions.sql：system:feature:list/update、system:parameter:list/update、system:config-history:list + 3 菜单 + 超管授权。
- mall-gateway：/api/admin/feature-configs/**、/api/admin/system-parameters/**、/api/admin/config-history/** → lb://mall-system（ADMIN）。

## 4. Design References

- CHG-0022 requirement-design.md §2.0–§2.2（双表收敛模型/CAS/错误码）/§2.5（RBAC）；STORY-006-01-01-01 story-design §1–§4。

## 5. Dependencies

无（本 Change 起点 DU）。

## 6. Implementation Sketch

```
AdminController (@PreAuthorize)
 → XxxApplicationService
    ├─ validate（类型枚举/STRING 数值解析/min-max/BOOLEAN/JSON 解析）
    ├─ update CAS: UPDATE ... WHERE id=? AND version=?
    │     affected==0 → B0604；成功 version+1
    ├─ guard: builtIn 删除/改 key → B0601 拒绝
    └─ historyRepository.insert(同事务, old/new/operator/traceId/reason)
 → Mapper（MyBatis-Plus）→ mall_system
```

## 7. Pseudocode

```
updateParameter(key, req, operator):
  cur = repo.findByKey(key) ?: throw B0603
  if req.version != cur.version: throw B0604          // 前端过期版本前置校验（DB CAS 为最终防线）
  validateByType(cur.parameterType, req.configValue, cur.minValue, cur.maxValue)  // 失败 B0601
  affected = repo.casUpdate(key, req.version, req.configValue, ...)
  if affected == 0: throw B0604
  history.insert(PARAMETER, key, cur.configValue, req.configValue, operator, traceId)

deleteFeature(key, operator):
  cur = findByKey(key) ?: throw B0603
  if cur.builtIn: throw B0601("内置配置不可删除")
  repo.deleteById(cur.id); history.insert(FEATURE, key, cur.enabled, null, operator, traceId)
```
