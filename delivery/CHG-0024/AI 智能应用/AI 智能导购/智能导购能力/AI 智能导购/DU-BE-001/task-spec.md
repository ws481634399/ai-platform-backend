# DU Task Spec — DU-BE-001

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-001 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-001 与同目录 task-design.md（"怎么做"）配对互链。
> verifies 约定：任务项绑定 TC-NNN（红绿灯对象）；TC 定义在外部对应 Story 的 test-design.md，本文件只引用不新造。

## 0. 元信息

- DU id: DU-BE-001
- Change ID: CHG-0024
- Feature Path: AI 智能应用/AI 智能导购/智能导购能力/AI 智能导购
- 权威来源: story-design.md §5 / DU-BE-001

## 任务清单

- [ ] T1 路由配置：/api/ai/** → ai-service（随既有路由模式，stripPrefix=false）（verifies: TC-009）
- [ ] T2 安全矩阵：members→MEMBER / admin→ADMIN / shopping·compare·support→permitAll，既有 matcher 零改动（verifies: TC-009）
- [ ] T3 切片测试回归：新路径认证行为 + 既有 /api/mall/** 全部行为零回退（verifies: TC-009）

## Acceptance Criteria

- [ ] AC-012 — 网关 /api/ai/** 路由与角色矩阵就绪：members 无 token 401、非 MEMBER 403；admin 非 ADMIN 403；shopping permitAll 放行；ai-service 下线时网关返回可控错误；既有规则行为回归全绿

## 执行顺序（Execution Order）

1. T1 → 2. T2 → 3. T3

## 并行度（Parallelization）

无（全串行，T3 依赖 T1/T2）。

## Verification

- Unit: N/A（配置类改动）
- Integration: gateway 切片测试（WebTestClient）：/api/ai/members/** 401/403/200 矩阵 + 既有路由回归（TC-009）
- API: N/A
- Migration: N/A（无库表变更）
- Error Case: ai-service 不可达 → 网关可控错误响应（断言非挂起/非堆栈泄漏）；无 token 访问 members → 401
