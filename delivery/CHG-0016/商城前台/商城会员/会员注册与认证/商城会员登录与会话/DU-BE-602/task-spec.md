# DU Task Spec — DU-BE-602

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-602
- Change ID: CHG-0016
- Feature Path: 商城前台/商城会员/会员注册与认证/商城会员登录与会话
- 权威来源: story-design.md §5 / DU-BE-602

## 任务清单

- [ ] 任务 1 — login 服务/Controller：统一凭据错误文案、禁用账号拒绝、双 Token 签发（verifies: TC-001, TC-002, TC-003）
- [ ] 任务 2 — refresh 旋转（哈希存储、family、ROTATED/REVOKED）与重放整族撤销（verifies: TC-004, TC-005）
- [ ] 任务 3 — logout 撤销 refresh + auth_version（verifies: TC-006）
- [ ] 任务 4 — 网关白名单与 ROLE_MEMBER/ROLE_ADMIN 双向隔离矩阵（verifies: TC-007, TC-008）

## Acceptance Criteria

- [ ] AC-008 — 正确凭据登录得双 Token；access claim subjectType=MEMBER、sub=memberId 字符串。
- [ ] AC-009 — 错密码/不存在用户同一文案"用户名或密码错误"（不可区分）。
- [ ] AC-010 — DISABLED 登录拒绝且文案"账号已禁用"。
- [ ] AC-011 — refresh 换新；旧 refresh 失效；重放已用 token 整族撤销 401；过期/伪造 401。
- [ ] AC-012 — logout 后 refresh 再用 401；旧 access 在版本/过期语义下被拒。
- [ ] AC-013 — MEMBER 调 /api/admin/** 403；ADMIN 调 /api/mall/members/me 403。

## 执行顺序（Execution Order）

1. 任务 1 → 2 → 3 → 4（网关矩阵可与 2/3 并行编码）。

## 并行度（Parallelization）

任务 4（网关）与任务 2/3 并行。

## Verification

- Unit: family 状态机三态、统一文案分支。
- Integration: MockMvc 登录/旋转/重放/退出；查库断言 ROTATED/REVOKED。
- API: 8080 网关两 Token 角色矩阵 403/200。
- Migration: N/A（V2 表在 DU-BE-601 任务 1）。
- Error Case: 伪造/过期/重放 refresh 一律 401，不泄露账号存在性。
