# DU Task Design — DU-BE-001

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-001 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-001 与同目录 task-spec.md（"做什么/验收"）配对互链。
> 本文件固定为 Expected Implementation（Plan / Sketch / Pseudocode），
> 与 implementation.md（Actual Implementation）分立，不得合并。

## 1. Goal

mall-gateway 新增 `/api/ai/**` 路由到 ai-service，并扩展安全角色矩阵（members 强制 MEMBER、admin 强制 ADMIN、shopping/compare/support permitAll），为 M6 四个 AI 应用提供统一认证边界（story-design.md §5 DU-BE-001 职责）。

## 2. Repository

repo-1（implementation/ai-platform-backend，mall-gateway 模块）。

## 3. Scope

- mall-gateway：路由定义（application.yml / nacos 路由配置，随既有模式）
- mall-gateway：`GatewaySecurityConfiguration`（追加 pathMatchers 三类规则，不动既有 matcher）
- mall-gateway 测试：路由转发与安全矩阵切片测试回归

## 4. Design References

- requirement-design.md §2.2（新增改动 repo-1）、§2.0（总体策略 1/2）、§4（Integration Boundary）
- story-design.md §1（模块改动 repo-1 节）、§6（测试策略 TC-009 网关部分）

## 5. Dependencies

无（权威表 depends on 为 "—"）。

## 6. Implementation Sketch

```
GatewaySecurityConfiguration（既有）
  pathMatchers 追加（顺序在既有 /api/mall/** 规则之后、anyExchange 之前）:
    "/api/ai/members/**"  → hasRole("ROLE_MEMBER")   # 会员 AI 端点（订单助手）
    "/api/ai/admin/**"    → hasRole("ROLE_ADMIN")    # 管理端点（知识库管理）
    "/api/ai/shopping/**","/api/ai/compare/**","/api/ai/support/**" → permitAll
  既有规则零改动（/api/mall/orders/** 等保持 MEMBER）
路由（随既有 route 定义模式）
    /api/ai/** → ai-service（uri 指向 ai-service 服务地址，stripPrefix=false）
响应头：TraceIdWebFilter 既有逻辑自动覆盖 /api/ai/**（无需改动）
```

错误处理路径：未认证访问 members/admin → 既有 401/403 信封（含既有安全链短路行为）；ai-service 不可达 → 网关既有 503 兜底。

## 7. Pseudocode

N/A——未命中 complexity-trigger（纯配置/安全规则追加，无算法与状态流转；行为由切片测试直接断言）。
