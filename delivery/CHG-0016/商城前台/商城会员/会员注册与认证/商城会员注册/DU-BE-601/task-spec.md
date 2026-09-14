# DU Task Spec — DU-BE-601

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-601
- Change ID: CHG-0016
- Feature Path: 商城前台/商城会员/会员注册与认证/商城会员注册
- 权威来源: story-design.md §5 / DU-BE-601

## 任务清单

- [ ] 任务 1 — identity V2 迁移（member_user/member_refresh_token/member_event_outbox）与 member V1 member_profile（verifies: TC-009）
- [ ] 任务 2 — MemberAccount 领域（规则/归一化/BCrypt）+ 注册 Controller/Service 单事务写账号+outbox（verifies: TC-001, TC-003, TC-007, TC-008）
- [ ] 任务 3 — 重复用户名（大小写变体）唯一冲突 409（verifies: TC-002, TC-009）
- [ ] 任务 4 — outbox relay：afterCommit 立即投递 + @Scheduled 30s 重试；MemberProvisionClient 带 X-Internal-Token（verifies: TC-006）
- [ ] 任务 5 — member provision 端点双幂等（eventId uk + memberId 判定）与默认 profile（verifies: TC-004, TC-005）
- [ ] 任务 6 — JwtIssuer 增 SubjectType 参并修正全部既有调用点（verifies: TC-001）

## Acceptance Criteria

- [ ] AC-001 — 201 返回字符串 memberId，可立即登录；响应与日志无明文密码（BCrypt 存储）。
- [ ] AC-002 — username_norm 唯一；AbC/abc 视为同名 409，仅一行。
- [ ] AC-003 — 用户名/密码规则矩阵违例 400 字段级提示。
- [ ] AC-004 — 注册成功同库事务后 provision，member_profile 存在且 memberId 一致、默认昵称。
- [ ] AC-005 — 同 eventId provision 重放仅一行，第二次 provisioned=false。
- [ ] AC-006 — member 首次不可用：outbox PENDING → 定时重试 DONE；profile 缺失可 seed 重建。
- [ ] AC-007 — outbox 插入失败致事务回滚，无 member_user 残留，同名可再注册。

## 执行顺序（Execution Order）

1. 任务 1 → 2 → 6（可并行于 3/5）→ 4 → 5；联调顺序 4 依赖 5 端点先合入（可先 mock）。

## 并行度（Parallelization）

任务 1（迁移）先行；2/6 与 5 可并行（identity、member 两服务）；4 在两端契约冻结后。

## Verification

- Unit: MemberAccount 规则矩阵、归一化；provision 幂等判定。
- Integration: @SpringBootTest 两服务 slice + MockRestServiceServer；@Scheduled 手动触发；事务回滚断言。
- API: 201/409/400 契约；立即登录。
- Migration: 干净库 V2/V1 migrate，uk_username_norm、uk_initialized_event_id 存在。
- Error Case: member 宕机 PENDING→DONE；outbox 失败回滚无残留。
