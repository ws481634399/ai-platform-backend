# DU Task Design — DU-BE-601

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-601 引用该表。

## 1. Goal

会员注册全链路：identity 独立账号库（member_user/member_refresh_token/member_event_outbox，V2）、注册应用服务、issuer 泛化 SubjectType、Outbox-Lite 开通投递（afterCommit RestClient + 定时重试）、member 侧 provision 双幂等与 profile V1。

## 2. Repository

repo-1（mall-identity 8101 + mall-member 8102 + mall-common-security/mall-contracts）

## 3. Scope

- mall-identity：db/migration V2（member_user uk_username_norm、member_refresh_token、member_event_outbox）；domain/memberaccount（聚合根+BCrypt+规则）；application RegisterMemberService（同库事务：账号+outbox）；interfaces `POST /api/auth/member/register`；outbox relay（@Scheduled 30s 扫描 PENDING，RestClient POST member internal，X-Internal-Token，afterCommit 立即尝试一次）；JwtIssuer 增 SubjectType 参（ADMIN 调用点保持不变）；MemberProvisionClient。
- mall-member：db/migration V1 member_profile（uk initialized_event_id）；internal `POST /api/internal/members/provision`（双幂等：eventId 唯一 + memberId 存在判定，返回 provisioned）；`GET /api/internal/members/{id}/profile-seed`；provisioning 包（RestClient 带 X-Internal-Token 回调 identity）。
- 配置：两服务 shared-secret 占位（DU-BE-501 机制）。

## 4. Design References

- CHG-0016 requirement-design.md §2.1（Outbox-Lite 方案与双兜底）、§4（/api/auth/member/** 与 internal 契约）、§5 数据模型与迁移。
- STORY-003-01-01-01 story-design.md §1 模块改动、§2 接口契约细化、§4 错误处理（409/400）。

## 5. Dependencies

权威表：无。表外跨 Change 依赖：DU-BE-501（@StringId、InternalIdentityFilter/X-Internal-Token，CHG-0015 先行）。

## 6. Implementation Sketch

- 注册流（单事务）：参数校验（用户名 4-20 字母开头+字母数字下划线；密码 ≥8 非纯数字）→ username_norm=LOWER(username) 查重（捕获 DuplicateKey → 409 USERNAME_TAKEN）→ BCrypt 哈希 → insert member_user(status=ACTIVE, auth_version=1) → insert outbox(eventId=UUID, type=MEMBER_PROVISIONED, payload{memberId,username,nickname}, status=PENDING, next_attempt_at=now) → commit。
- afterCommit：Relay 立即 trySend(eventId)；失败保留 PENDING + attempt++/next_attempt=+30s（上限退避，max attempts 后留 PENDING 告警，不 DLQ）。
- provision：member 收事件 → 查 eventId uk（存在即幂等返回 provisioned:false）→ 查 memberId（已存在也 false）→ insert member_profile（initialized_event_id=eventId，默认昵称"会员"+后 6 位）→ provisioned:true。
- 懒补偿（DU-BE-603 也会用到同端点）：member 发现 profile 缺失 → GET identity profile-seed{username, nickname} → 401/404 → 向上 401 并要求重新登录。
- DTO @StringId：memberId 响应字符串；日志 MASK：toString 不含 password，log 中禁止 password 键。
- issuer：issue(subjectId, subjectType)，ADMIN 原调用补 SubjectType.ADMIN；MEMBER 路径 claim subjectType=MEMBER，JwtSubjectConverter 已生成 ROLE_MEMBER。

## 7. Pseudocode

N/A（metadata `pseudocode: false`）。注册为直线编排+声明式事务/唯一约束兜底，控制流已在 §6 完整表达；relay 重试为定时扫描单循环无状态分支。
