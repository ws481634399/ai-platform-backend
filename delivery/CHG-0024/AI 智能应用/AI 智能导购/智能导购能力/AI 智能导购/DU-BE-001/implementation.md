# DU Implementation — DU-BE-001

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

- mall-gateway 路由表（`src/main/resources/application.yml`）：新增 `ai-service` 路由，`/api/ai/**` → `${MALL_GATEWAY_AI_URI:http://localhost:8120}`（直连模式随既有路由，stripPrefix=false，ai-service 按完整路径收口）。
- mall-gateway 安全矩阵（`src/main/java/com/ai/mall/gateway/security/GatewaySecurityConfiguration.java`）：
  - permitAll 白名单追加 `/api/ai/shopping/**`、`/api/ai/compare/**`、`/api/ai/support/**`（GUEST 可用，功能开关在 ai-service 内 fail-closed）；
  - 新增 `/api/ai/members/**` → `hasRole("MEMBER")`、`/api/ai/admin/**` → `hasRole("ADMIN")`；既有 matcher 零改动。
- mall-identity 迁移（`src/main/resources/db/migration/V12__ai_knowledge_permissions.sql`）：`ai:knowledge:*` 5 个权限点 + 「AI 智能应用」目录与「知识库管理」菜单 + 超管授权（目录/页面同授权）。
- 测试：
  - `CHG0024GatewayAiSecurityChainTest`（6 用例）：真实加载安全链，验证 AI 三端点匿名放行、AI 会员端点 401/MEMBER 200/ADMIN 403、AI 管理端点 MEMBER 403/ADMIN 200、既有矩阵回归（/api/internal/** 404、公开商品放行）；
  - `CHG0024GatewayAiRouteContractTest`（1 用例）：路由契约断言 `/api/ai/**` 与默认直连 URI。

## Commits

- repo-1（implementation/ai-platform-backend）：本地提交（见下），未推送。

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。 -->

无

## 自检

- [x] task-spec.md 任务项全部完成（本 DU 无 T 编号任务清单，Acceptance AC-012 对应网关路由与安全矩阵落地）
- [x] `mvn -pl mall-gateway -am test` 全绿：34 tests, 0 failures（含新增 CHG-0024 7 用例 + 既有矩阵回归）
- [x] 未触碰既有 matcher/路由（仅追加），CHG-0015/0016 既有切片测试回归通过
- [x] evidence：测试输出见 evidence/（EV-001）
